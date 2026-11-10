# TP2 — Application Web LangChain4j

Application Maven WAR de Yassine Atiki, partie Web du TP2 de Richard Grin.

## Exécuter

Environnement validé : Java 25.0.4.1, Maven 3.9.14, Payara Server 7.2026.10 (build 18), Jakarta EE Web 11.0.0, PrimeFaces 16.0.0, LangChain4j 1.22.0 et Google GenAI 1.22.0-beta32.

1. Définir `GEMINI_KEY` dans l'environnement du processus qui démarre Payara. Si la variable vient d'être ajoutée, redémarrer l'IDE et le serveur pour qu'ils l'héritent. La clé n'est lue que par `System.getenv("GEMINI_KEY")`, jamais dans un fichier du projet. Sans cette variable, l'initialisation du client échoue.
2. Compiler depuis la racine du projet :

   ```powershell
   mvn clean package
   ```

3. Démarrer Payara 7, puis déployer le WAR (ou utiliser l'artifact WAR dans IntelliJ) :

   ```powershell
   asadmin --host localhost --port 4848 deploy --force=true --name tp2-web-yassine-atiki --contextroot tp2-web-yassine-atiki target/tp2-web-yassine-atiki-1.0-SNAPSHOT.war
   ```

4. Ouvrir [l'application locale](http://localhost:8080/tp2-web-yassine-atiki/).

Le modèle est `gemini-3.5-flash-lite`. Les appels nécessitent un accès réseau à Gemini et une clé autorisée pour ce modèle.

## Structure et comportement

- `src/main/webapp/index.xhtml` reprend la page JSF du TP0, sans mode debug. Le JS et le CSS sont sous `resources/js` et `resources/css`.
- `jsf/Bb.java` est `@Named @ViewScoped` et `Serializable`. Sa méthode `envoyer()` délègue à l'instance injectée de `LlmClient`.
- `llm/Assistant.java` définit `String chat(String prompt)`.
- `llm/LlmClient.java` est `@Dependent Serializable`. Il construit un `GoogleGenAiChatModel` et un service `AiServices` avec `MessageWindowChatMemory.withMaxMessages(20)`.
- Le rôle sélectionné est un vrai `SystemMessage`, placé avant la première question. Le menu reste figé pendant la discussion ; « Nouveau chat » efface le contexte et crée une nouvelle vue, permettant de choisir un autre rôle.
- Les objets LangChain4j restent `transient`. Les messages sont conservés sous forme JSON sérialisable et restaurés dans la mémoire après désérialisation. La clé et le modèle ne sont pas sérialisés. Une requête échouée restaure la mémoire précédente.
- `WEB-INF/web.xml` active Faces, la page d'accueil `index.xhtml` et le mode `Production`.

Les options de tokens cumulatifs, RAG et streaming ne sont pas incluses dans cette partie Web.

## Sources et adaptations

Consignes : [partie Web](http://richard.grin.free.fr/emsi/casablanca-ia/tp/tp2-langchain4j/index.html#partieweb), [accès au LLM](http://richard.grin.free.fr/emsi/casablanca-ia/tp/tp2-langchain4j/index.html#accesllm).

L'énoncé complet et les corrections [page TP0](http://richard.grin.free.fr/emsi/casablanca-ia/tp/tp0-jakartaee/correction/page.html), [ressources TP0](http://richard.grin.free.fr/emsi/casablanca-ia/tp/tp0-jakartaee/correction/ressources.html) et [backing bean TP0](http://richard.grin.free.fr/emsi/casablanca-ia/tp/tp0-jakartaee/correction/backingbean.html) ont été consultés le 10 novembre 2026 (HTTP 200). Les six liens de correction TP2 fournis dans l'énoncé renvoyaient HTTP 403. Le code Web est donc une adaptation du TP0 local, recoupée avec sa correction publique. Le client LLM et la gestion de passivation sont une implémentation de ces consignes, et ne sont pas présentés comme une copie de la correction TP2.

Le projet TP0 et le projet des Tests 1–7 n'ont pas été modifiés. L'initialisation Git existante est conservée. Les fichiers IntelliJ déjà suivis ont été retirés du suivi sans être supprimés sur le poste ; `.idea/`, `target/` et les fichiers `.env` sont ignorés.

## Validation et bonus Web

Les cinq tests JUnit utilisent un modèle factice, sans appel Gemini : rôle système et historique, changement de rôle/reset, limite mémoire, sérialisation, absence de contamination après erreur.

Les résultats réels de Payara et Gemini sont conservés dans [le rapport](docs/validation-web.md) et [les sorties exactes](docs/web-validation.json). Le [brouillon du courriel](docs/email-bonus-web.md) est séparé du bonus des sept tests. Il n'a pas été envoyé.

Pour reproduire les vérifications sur l'application déployée, avec Python 3 et cinq appels réels à Gemini :

```powershell
python scripts/validate_web.py --base-url http://localhost:8080/tp2-web-yassine-atiki/ --output target/web-validation.json
```

Le script soumet le formulaire JSF et conserve cookies et état de vue uniquement en mémoire. Il n'appelle pas directement l'API Gemini. Il contrôle les réponses HTML ; il n'exécute pas le JavaScript du navigateur.

Le développement, les tests et les commits ont été effectués avec l'assistance de Codex et de ses agents. L'identité Git préconfigurée a été conservée.
