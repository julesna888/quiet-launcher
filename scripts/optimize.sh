#!/bin/sh
# Riapplica le ottimizzazioni al Fire TV Stick Lite. Uso: scripts/optimize.sh <ip-del-firestick>
# Disattiva servizi Amazon (telemetria, anteprime, tutorial, aggiornamenti forzati) e dimezza le animazioni.
# ATTENZIONE: include tv.forcedotaupdater/tv.easyupgrade, quindi gli aggiornamenti di sistema non arrivano più da soli.
# Annulla un pacchetto: adb shell pm enable <pacchetto>
IP=${1:?uso: $0 <ip-firestick>}; A="adb -s $IP:5555 shell"
adb connect "$IP:5555" >/dev/null
for p in bueller.music bueller.photos dp.logger gamehub hybridadidservice media.recommendations minitv.android.app naatyam perfc perfcollection shoptv.client shoptv.firetv.client sneakpeek storm.lightning.tutorial tmm.tutorial tv.acr tv.alexaalerts tv.fw.metrics tv.matter tv.mattercohost tv.releasenotes wirelessmetrics.service aca prism.android.service ceviche ftv.screensaver tv.forcedotaupdater.v2 tv.easyupgrade; do
  $A "pm disable-user --user 0 com.amazon.$p" 2>&1 | head -1
done
for k in window_animation_scale transition_animation_scale animator_duration_scale; do $A "settings put global $k 0.5"; done
echo "RAM libera:"; $A "grep MemAvailable /proc/meminfo"
