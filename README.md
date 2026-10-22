# À répondre

App Android qui lit les notifications de messages (WhatsApp, Instagram, Messenger, SMS, Telegram…) et garde la liste des conversations auxquelles tu n'as pas encore répondu.

Pour l'instant elle fait trois choses :
- elle capte chaque notification et **ne garde que les messages** (pas les likes, mises à jour, musique, résumés de groupe…) ;
- elle les stocke **uniquement sur le téléphone** (SQLite), sans doublons ;
- elle les affiche regroupés par conversation, avec un bouton « Répondu ✅ ».

## Installer sur le téléphone
1. Onglet **Actions** du repo → dernier run vert → artefact `a-repondre-apk` → dézipper → `app-debug.apk`.
2. Ouvrir l'APK sur le téléphone (autoriser « installer des apps inconnues »).
3. Ouvrir l'app → « Autoriser l'accès aux notifications » → activer **À répondre**.

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
