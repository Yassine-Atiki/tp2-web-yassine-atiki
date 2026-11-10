# Validation de la partie Web du TP2

Date : **10 novembre 2026**, fuseau **Africa/Casablanca (UTC+01:00)**.

## État initial et périmètre

Le projet local demandé `C:\Users\USER PC\IdeaProjects\tp2-web-yassine-atiki` a été réellement inspecté et modifié. Il était propre, sur `master`, au commit `33e6d4b`, aligné sur `origin/master`. L'origine était déjà `https://github.com/Yassine-Atiki/tp2-web-yassine-atiki.git`. L'identité Git existante a été utilisée sans la changer.

Le TP0 a été inspecté en lecture seule (état propre, commit `bb5b535`). Son interface JSF, son JS et son CSS ont été repris. Le projet des Tests 1–7 n'a pas été modifié. Aucun nouveau dépôt n'a été créé et aucun historique n'a été réécrit.

## Environnement réellement utilisé

| Élément | Version ou configuration |
|---|---|
| Poste | Windows, JDK 25.0.4.1 |
| Maven | 3.9.14 |
| Serveur | Payara Server 7.2026.10 (build 18) |
| API Jakarta | Jakarta EE Web 11.0.0, fournie par le serveur |
| PrimeFaces | 16.0.0 |
| LangChain4j | 1.22.0 |
| Intégration Gemini | langchain4j-google-genai 1.22.0-beta32 |
| Modèle réel | gemini-3.5-flash-lite |
| Clé | GEMINI_KEY héritée par Payara ; aucune valeur enregistrée |
| Domaine d'essai | tp2web, isolé du domain1 existant, listeners limités à 127.0.0.1 |
| URL | http://localhost:8080/tp2-web-yassine-atiki/ |

## Build et déploiement

Commande réellement exécutée, avec `pom.xml` du projet cible :

```powershell
mvn -B -ntp -f "C:\Users\USER PC\IdeaProjects\tp2-web-yassine-atiki\pom.xml" clean package
```

Résultat : **BUILD SUCCESS**, le 10 novembre 2026 à **13:10:39 +01:00**, durée Maven **59.995 s**. Le WAR a été produit dans `target/tp2-web-yassine-atiki-1.0-SNAPSHOT.war`.

JUnit : **5 tests, 0 échec, 0 erreur, 0 ignoré**. Le fichier [test-unitaires.txt](test-unitaires.txt) est une copie du résultat Surefire réellement produit. Ces tests emploient un modèle factice, sans accès à Gemini. Ils contrôlent les messages transmis, le rôle système, le vidage de mémoire, la limite 20, la sérialisation/restauration et l'absence de contamination après échec.

Commandes de contrôle et déploiement réellement exécutées :

```powershell
& 'C:\dev\payara7\bin\asadmin.bat' --host localhost --port 4848 --interactive=false version
& 'C:\dev\payara7\bin\asadmin.bat' --host localhost --port 4848 --interactive=false deploy --name tp2-web-yassine-atiki --contextroot tp2-web-yassine-atiki 'C:\Users\USER PC\IdeaProjects\tp2-web-yassine-atiki\target\tp2-web-yassine-atiki-1.0-SNAPSHOT.war'
```

Résultats : `Payara Server 7.2026.10 (build 18)`, puis `Application deployed with name tp2-web-yassine-atiki.` et `Command deploy executed successfully.` Le journal serveur confirme le déploiement à **13:12:00 +01:00**.

## Interactions Web et Gemini réelles

Exécution du **13:13:58 au 13:14:39**, heure de Casablanca. **10 contrôles réussis, 5 soumissions à Gemini**. Le script Python soumet le vrai formulaire JSF avec ses cookies et son ViewState ; il ne contourne pas le backing bean par un appel direct à Gemini.

| Contrôle | Observation réelle | Résultat |
|---|---|---|
| Vue initiale | HTTP 200, champs vides, rôle Assistant disponible | Réussi |
| Question vide | « Il manque le texte de la question. », aucun historique ajouté | Réussi |
| Traducteur + « bonjour » | **« Hello »**, rôle ensuite figé | Réussi |
| Nouveau chat après traduction | Question, réponse et historique vides ; menu réactivé | Réussi |
| Mémorisation | « C'est mémorisé : AZUR-CEDRE-47. » | Réussi |
| Rappel dans la même vue | « Le repère fictif que tu m'as donné est : AZUR-CEDRE-47. » | Réussi |
| Nouveau chat après les deux échanges | Champs vidés, menu réactivé | Réussi |
| Rappel dans la nouvelle discussion | Gemini indique qu'il ne connaît pas le repère ; aucun AZUR-CEDRE-47 dans la réponse | Réussi |
| Nouveau chat avant changement de rôle | Champs vidés, menu réactivé | Réussi |
| Guide touristique + « Rabat » | Lieux à visiter et estimation du prix d'un repas, sans réponse de traducteur | Réussi |

La réponse exacte après remise à zéro est :

> Je ne connais pas ce repère fictif, car notre conversation commence à l'instant et je n'ai pas accès à l'historique d'éventuelles discussions précédentes (ou tu ne m'en as pas encore donné dans celle-ci).

Toutes les questions, réponses complètes, durées et historiques sont dans [web-validation.json](web-validation.json), conservé sans cookies ni ViewState. Le champ `role: null` du test de rappel signifie que le script conserve le rôle Assistant déjà choisi ; il ne signifie pas que le rôle système métier est absent. Les estimations touristiques de Gemini sont des sorties de test, dont l'exactitude factuelle n'a pas été vérifiée.

Pour reproduire après déploiement :

```powershell
python scripts/validate_web.py --base-url http://localhost:8080/tp2-web-yassine-atiki/ --output target/web-validation.json
```

## Fichiers concernés et limites

Fichiers ajoutés ou adaptés : `pom.xml`, `.gitignore`, `index.xhtml`, `resources/js/script.js`, `resources/css/mycsslayout.css`, `WEB-INF/web.xml`, `jsf/Bb.java`, `llm/Assistant.java`, `llm/LlmClient.java`, `LlmClientTest.java`, le script de validation et cette documentation. Le squelette Java/JSP initial a été conservé ; l'accueil utilise désormais JSF. Les fichiers `.idea` initialement suivis ont été retirés de Git sans suppression locale.

La sérialisation a été vérifiée par test Java, avec un modèle factice non sérialisable et une restauration de l'historique. Une passivation/reprise forcée du serveur Payara n'a pas été provoquée. Le contrôle du navigateur n'a pas répondu (délais dépassés) : le rendu visuel, les boutons JavaScript de copie et l'effacement côté navigateur restent non vérifiés visuellement. Les parcours serveur JSF ont, eux, été exécutés avec Gemini réel.

Le domaine `domain1` préexistant n'a pas été modifié ; le domaine isolé `tp2web` est resté démarré après les essais. Les journaux techniques et les fichiers du domaine ne sont pas publiés. Le code lit seulement la clé depuis l'environnement ; les traces de requêtes/réponses du fournisseur sont désactivées.

## Commits progressifs

| Phase | Commit |
|---|---|
| Initialisation déjà réalisée par l'étudiant | [33e6d4b](https://github.com/Yassine-Atiki/tp2-web-yassine-atiki/commit/33e6d4bf9b350ff6fb2eadfb820fb804e6c08cca) |
| Page JSF, ressources et PrimeFaces | [184d0e6](https://github.com/Yassine-Atiki/tp2-web-yassine-atiki/commit/184d0e68d3862d1f38a268407c105019c92785a2) |
| Backing bean | [b95e58a](https://github.com/Yassine-Atiki/tp2-web-yassine-atiki/commit/b95e58aa355812ffe77cbce9976a37a0a4f2b00b) |
| Client LLM, assistant et tests | [c6c1c15](https://github.com/Yassine-Atiki/tp2-web-yassine-atiki/commit/c6c1c159c0102ac4c08c3616c40829321bf1c356) |
| Configuration JSF validée sur Payara | [5c80ad6](https://github.com/Yassine-Atiki/tp2-web-yassine-atiki/commit/5c80ad6a5a85094cb7c26dcb5f36cb42bf9691fe) |

Chaque nouveau commit de code ci-dessus a été suivi d'un push sur `origin/master`. Le dernier commit de documentation contient les preuves et le brouillon de courriel. Les commits distants ont été contrôlés sur GitHub après push.

## Bonus Web

Le [brouillon du courriel](email-bonus-web.md) contient le sujet exact **TP 2 - Web terminé** et l'URL du dépôt Web. Il n'a pas été envoyé. Il est distinct du courriel du bonus des Tests 1–7.
