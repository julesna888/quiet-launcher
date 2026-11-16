#!/bin/sh
# Installa il launcher e fa tornare il tasto Home su di esso.
# Uso: scripts/install.sh <ip-del-firestick> [percorso-apk]
# Prima: sul Fire TV attiva Impostazioni > My Fire TV > Opzioni sviluppatore > Debug ADB.
IP=${1:?uso: $0 <ip-firestick> [apk]}; APK=${2:-app/out/launcher.apk}
adb connect "$IP:5555" >/dev/null && A="adb -s $IP:5555"
$A install -r "$APK"
# Fire OS 7: servizio di accessibilità HomeGuard
$A shell settings put secure enabled_accessibility_services app.quiet.launcher/app.quiet.launcher.HomeGuard
$A shell settings put secure accessibility_enabled 1
# Fire OS 6 cancella subito l'impostazione qui sopra: lì HomeWatch usa l'accesso ai dati di utilizzo
$A shell appops set app.quiet.launcher GET_USAGE_STATS allow
$A shell am start -n app.quiet.launcher/.MainActivity
echo "Fatto. Al primo avvio scegli le tue app."
echo "Per tornare alla home Amazon:"
echo "  $A shell settings delete secure enabled_accessibility_services"
echo "  $A shell appops set app.quiet.launcher GET_USAGE_STATS default"
