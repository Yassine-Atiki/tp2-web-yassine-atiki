#!/usr/bin/env python3
"""Validate the deployed JSF application through its real HTML form.

Uses only the Python standard library. Cookies and JSF ViewState stay in memory.
Five sequential Gemini interactions are requested; no provider API is called directly.
"""
import argparse
import datetime
import http.cookiejar
import json
import re
import time
import urllib.parse
import urllib.request
from html.parser import HTMLParser
from pathlib import Path


class JsfForm(HTMLParser):
    def __init__(self, html, url):
        super().__init__(convert_charrefs=True)
        self.url = url
        self.action = None
        self.fields = {}
        self.buttons = {}
        self.textareas = {}
        self.options = []
        self.role_disabled = False
        self.text = []
        self._in_form = False
        self._textarea = None
        self._select = None
        self._option = None
        self.feed(html)
        if not self.action or 'jakarta.faces.ViewState' not in self.fields:
            raise AssertionError('The response does not contain the JSF form and ViewState.')

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == 'form' and a.get('id') == 'form':
            self._in_form = True
            self.action = urllib.parse.urljoin(self.url, a['action'])
        if not self._in_form:
            return
        if tag == 'input' and a.get('name'):
            name = a['name']
            kind = a.get('type', 'text').lower()
            if kind == 'submit':
                self.buttons[a.get('value', '')] = name
            elif 'disabled' not in a and kind not in ('button', 'reset', 'checkbox', 'radio'):
                self.fields[name] = a.get('value', '')
        elif tag == 'textarea' and a.get('name'):
            self._textarea = [a['name'], []]
        elif tag == 'select' and a.get('name'):
            self._select = a['name']
            if self._select.endswith(':rolesysteme_input'):
                self.role_disabled = 'disabled' in a
        elif tag == 'option' and self._select:
            self._option = [a.get('value', ''), [], 'selected' in a]

    def handle_data(self, data):
        self.text.append(data)
        if self._textarea:
            self._textarea[1].append(data)
        if self._option:
            self._option[1].append(data)

    def handle_endtag(self, tag):
        if tag == 'textarea' and self._textarea:
            name, parts = self._textarea
            value = ''.join(parts)
            self.textareas[name] = value
            self.fields[name] = value
            self._textarea = None
        elif tag == 'option' and self._option:
            value, parts, selected = self._option
            label = ''.join(parts)
            if self._select.endswith(':rolesysteme_input'):
                self.options.append((label, value))
            if selected:
                self.fields[self._select] = value
            self._option = None
        elif tag == 'select':
            self._select = None
        elif tag == 'form':
            self._in_form = False

    def area(self, short_name):
        return self.textareas.get('form:' + short_name, '')

    def role(self):
        value = self.fields.get('form:rolesysteme_input')
        return next((label for label, option_value in self.options if option_value == value), None)


class Validator:
    def __init__(self, base_url, output):
        self.base_url = base_url.rstrip('/') + '/'
        self.output = Path(output)
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.report = {
            'started_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'base_url': self.base_url,
            'method': 'Sequential HTTP requests to the real JSF form, with cookies and ViewState retained in memory only.',
            'llm_submissions': 0,
            'results': [],
            'status': 'running',
        }
        self.form = None

    def save(self):
        self.output.parent.mkdir(parents=True, exist_ok=True)
        self.output.write_text(json.dumps(self.report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

    def request(self, url, data=None):
        request = urllib.request.Request(url, data=data)
        if data is not None:
            request.add_header('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8')
        started = time.monotonic()
        with self.opener.open(request, timeout=150) as response:
            html = response.read().decode(response.headers.get_content_charset() or 'utf-8')
            status = response.status
            final_url = response.url
        if status != 200:
            raise AssertionError('Unexpected HTTP status: ' + str(status))
        self.form = JsfForm(html, final_url)
        return round(time.monotonic() - started, 3)

    def post(self, button, question=None, role_prefix=None):
        fields = dict(self.form.fields)
        if question is not None:
            fields['form:question'] = question
        if role_prefix is not None:
            label, role_value = next((label, value) for label, value in self.form.options
                                     if label.startswith(role_prefix))
            fields['form:rolesysteme_input'] = role_value
            fields['form:rolesysteme_editableInput'] = label
        fields[self.form.buttons[button]] = button
        return self.request(self.form.action, urllib.parse.urlencode(fields).encode('utf-8'))

    def record(self, name, passed, **details):
        self.report['results'].append({'test': name, 'passed': bool(passed), **details})
        self.save()
        print(name + ': ' + ('PASS' if passed else 'FAIL'), flush=True)
        if not passed:
            raise AssertionError('Validation failed: ' + name)

    def reset(self, name):
        duration = self.post('Nouveau chat')
        self.record(name,
                    not any(self.form.area(a) for a in ('question', 'reponse', 'conversation'))
                    and not self.form.role_disabled and self.form.role() == 'Assistant',
                    selected_role=self.form.role(), role_menu_enabled=not self.form.role_disabled,
                    question_empty=not self.form.area('question'),
                    response_empty=not self.form.area('reponse'),
                    conversation_empty=not self.form.area('conversation'), duration_seconds=duration)

    def ask(self, name, question, role_prefix, check):
        self.report['llm_submissions'] += 1
        if self.report['llm_submissions'] > 5:
            raise AssertionError('The five-LLM-submission limit was reached.')
        self.save()
        duration = self.post('Envoyer la question', question, role_prefix)
        answer = self.form.area('reponse')
        conversation = self.form.area('conversation')
        passed = bool(answer.strip()) and question in conversation and check(answer, conversation)
        self.record(name, passed, role=role_prefix, question=question, response=answer,
                    conversation=conversation, role_menu_disabled=self.form.role_disabled,
                    duration_seconds=duration)
        if not self.form.role_disabled:
            raise AssertionError('Role menu should be disabled after a successful exchange.')
        return answer

    def run(self):
        try:
            duration = self.request(self.base_url)
            self.record('initial_empty_view',
                        not any(self.form.area(a) for a in ('question', 'reponse', 'conversation'))
                        and self.form.role() == 'Assistant' and not self.form.role_disabled,
                        selected_role=self.form.role(), role_menu_enabled=not self.form.role_disabled,
                        duration_seconds=duration)
            duration = self.post('Envoyer la question', '', 'Assistant')
            self.record('empty_question_rejected',
                        'Il manque le texte de la question.' in ''.join(self.form.text)
                        and not self.form.area('reponse') and not self.form.area('conversation'),
                        displayed_validation='Il manque le texte de la question.',
                        response_empty=not self.form.area('reponse'),
                        conversation_empty=not self.form.area('conversation'),
                        provider_call='No call expected: backing bean returns before LlmClient for blank text.',
                        duration_seconds=duration)
            self.ask('translator_bonjour', 'bonjour', 'Traducteur',
                     lambda answer, _: bool(re.search(r'\b(hello|good morning|good day|hi)\b', answer, re.I)))
            self.reset('new_chat_after_translation')
            marker = 'AZUR-CEDRE-47'
            self.ask('remember_marker',
                     'M\u00e9morise ce rep\u00e8re fictif pour notre conversation : ' + marker + '. R\u00e9ponds simplement que tu l\u2019as m\u00e9moris\u00e9.',
                     'Assistant', lambda answer, _: bool(answer.strip()))
            self.ask('recall_same_view', 'Quel est le rep\u00e8re fictif que je viens de te donner ?', None,
                     lambda answer, _: marker in answer)
            self.reset('new_chat_clears_memory_and_view')
            self.ask('marker_absent_in_new_chat',
                     'Quel rep\u00e8re fictif ai-je donn\u00e9 dans une discussion pr\u00e9c\u00e9dente ? Si tu ne le connais pas, dis-le sans en inventer.',
                     'Assistant', lambda answer, _: marker not in answer)
            self.reset('new_chat_before_role_change')
            self.ask('guide_after_role_change', 'Rabat', 'Guide touristique',
                     lambda answer, _: bool(re.search(r'(kasbah|oudaya|hassan|chellah|mausol|visit|visite)', answer, re.I))
                     and bool(re.search(r'(repas|meal|MAD|dirham|restaurant|dining|food)', answer, re.I)))
            self.report['status'] = 'passed'
        except Exception as exc:
            self.report['status'] = 'failed'
            # Do not serialize HTTP headers, URLs carrying jsessionid, cookies, or ViewState.
            self.report['failure'] = type(exc).__name__ + ': ' + (
                str(exc) if isinstance(exc, AssertionError) else 'Request or parsing failed; inspect local environment.')
            raise
        finally:
            self.report['finished_at'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
            self.save()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://localhost:8080/tp2-web-yassine-atiki/')
    parser.add_argument('--output', default='target/web-validation.json')
    args = parser.parse_args()
    Validator(args.base_url, args.output).run()


if __name__ == '__main__':
    main()
