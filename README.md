# 3a4

Projet 3a4 créé par moez-og.

## Configuration OTP Email (Gmail SMTP)

Le flux `Forgot Password` utilise un OTP envoyé par email via Gmail SMTP.

Options de configuration (ordre de priorité):

1. Constantes dans `GmailOtpMailService` (`HARDCODED_*`)
2. Variables d'environnement `APP_GMAIL_USERNAME` et `APP_GMAIL_APP_PASSWORD`
3. Fichier local `local-secrets.properties` (non versionné)

Exemple de fichier local:

- Copier `local-secrets.example.properties` vers `local-secrets.properties`
- Remplir:
	- `gmail.username=your_email@gmail.com`
	- `gmail.appPassword=your_16_chars_app_password`

Règles implémentées:

- OTP valide 60 secondes
- 3 tentatives OTP maximum
- Après 3 échecs: vérification caméra (Face ID)
- Si Face ID échoue: blocage 30 secondes