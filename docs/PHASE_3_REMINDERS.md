# Fase 3 — Recordatorios persistentes

## Objetivo

Entregar avisos de repaso fiables sin alterar el algoritmo FSRS ni registrar actividad cognitiva inexistente.

## Decisiones

- WorkManager sustituye a `AlarmManager.setInexactRepeating`.
- El trabajo diario es un `OneTimeWorkRequest` que programa el siguiente al terminar. Así, la hora local se recalcula cada día y tolera cambios de horario de verano.
- La programación persiste cuando la aplicación se cierra y después de reinicios del dispositivo.
- No se solicita `SCHEDULE_EXACT_ALARM`; los avisos no requieren precisión de reloj despertador.
- El permiso `POST_NOTIFICATIONS` se solicita después de que el usuario elige una hora de recordatorio, no al abrir la aplicación.

## Flujo diario

1. WorkManager ejecuta `ReminderWorker` cerca de la hora configurada.
2. Room marca como omitidas las programaciones cuya ventana de gracia venció.
3. Se consultan los temas pendientes.
4. Se emite una notificación individual por tema, agrupada cuando hay varios.
5. El trabajador agenda el próximo ciclo diario.

## Acciones

- Tocar la notificación abre directamente el tema correspondiente.
- `30 min` vuelve a comprobar ese tema después de 30 minutos.
- `2 h` vuelve a comprobarlo después de 2 horas.
- `Mañana` lo comprueba al día siguiente en la hora diaria configurada.

Posponer solo modifica el aviso. No cambia `nextReviewAt`, estabilidad, dificultad, recuperabilidad, estado de cumplimiento ni historial de repaso.

Si el tema se repasa antes de que termine la posposición, el trabajador comprueba la nueva fecha FSRS y no muestra un aviso obsoleto.

## Agrupación

Se muestran como máximo seis notificaciones individuales simultáneas. Cuando existen varios temas, Android recibe además una notificación resumen. Los avisos anteriores se cancelan antes de reconstruir la cola diaria.

## Migración

Al iniciar o reconfigurar los avisos se busca y cancela la antigua alarma repetitiva con request code `9001`. Después, WorkManager queda como única fuente de programación.

## Limitación deliberada

WorkManager garantiza ejecución diferible, no una alarma exacta al minuto. El sistema operativo puede desplazar el trabajo por optimización energética. Esta conducta evita solicitar acceso especial a alarmas exactas.
