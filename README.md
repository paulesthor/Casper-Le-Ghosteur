# Casper

App Android qui lit les notifications de messages (WhatsApp, Instagram, Messenger, SMS, Telegram…) et garde la liste des conversations auxquelles tu n'as pas encore répondu.

Ce qu'elle fait :
- elle ne garde que les messages **WhatsApp, Instagram, Messenger, SMS** et les **appels manqués**, stockés uniquement sur le téléphone ;
- elle les affiche par conversation, **la personne qui attend depuis le plus longtemps en haut** ;
- toucher une conversation **l'ouvre directement dans son app** (appui long : le détail) ;
- elle voit quand tu as répondu : réponse depuis la notification, ou conversation ouverte dans l'app (réglable) ;
- **rappels** : par personne (« Maman : après 1 h, puis toutes les heures ») ou pour tout le monde (« après 24 h ») ;
- thème clair ou sombre selon le téléphone.

## Captures sans téléphone
`./gradlew testDebugUnitTest -Pscreenshots` génère les écrans dans `app/build/screenshots/`.

## Installer sur le téléphone
1. Onglet **Actions** du repo → dernier run vert → artefact `casper-apk` → dézipper → `app-debug.apk`.
2. Ouvrir l'APK sur le téléphone (autoriser « installer des apps inconnues »).
3. Ouvrir l'app → « Autoriser l'accès aux notifications » → activer **Casper**.

## Tester sur un émulateur
La CI le fait automatiquement (job `emulator`) : elle installe l'app sur un émulateur Android 14, poste une notification de chat et une notification normale, puis vérifie que seule la première est captée. Le log et une capture d'écran sont dans l'artefact `emulator-results`.

En local avec Android Studio : lancer un émulateur, puis
```
./gradlew assembleDebug
./scripts/emulator-test.sh
```
Pour tester à la main avec de faux messages :
```
adb shell "cmd notification post -S messaging --conversation Lea --message 'Lea:Tu viens samedi' t1 x"
```
