# Mejora de Voz Realtime para English Car

## Objetivo

Dar un giro a la aplicacion para que la conversacion con el asistente se sienta mas fluida, natural y cercana a hablar con otra persona, especialmente mientras el usuario conduce.

La prioridad no es agregar complejidad por agregarla. La prioridad es mejorar:

- baja latencia;
- interrupciones naturales;
- microfono activo durante la conversacion;
- voz mas natural;
- menos pausas entre lo que dice el usuario y lo que responde la IA;
- estabilidad con datos moviles;
- fallback seguro cuando internet falle.
- pronunciacion clara para usuarios que estan aprendiendo ingles.

## Principio de implementacion

No se debe reemplazar todo de una vez ni romper la version actual que ya funciona. La nueva arquitectura se implementara en una rama experimental y mantendra el modo actual como respaldo.

La aplicacion debe poder funcionar en dos modos:

1. **Modo actual estable**: AudioRecord + transcripcion backend + respuesta IA + TTS Android.
2. **Modo realtime experimental**: audio en streaming + OpenAI Realtime API + reproduccion inmediata de audio.

El usuario no debe ver opciones tecnicas como Oboe, RNNoise, Silero o WebRTC. La interfaz debe seguir mostrando opciones humanas y simples: asistente, voz, modelo, comandos, correcciones, tiempos, backend y modo de conversacion si aplica.

## Alcance recomendado

### Se implementa primero

- OpenAI Realtime API para conversacion bidireccional.
- Streaming de audio desde Android.
- Respuestas de audio en streaming.
- Interrupcion del asistente cuando el usuario empiece a hablar.
- AudioTrack para reproducir audio de baja latencia.
- Backend para crear sesiones seguras de Realtime sin exponer la API key en Android.
- Fallback automatico al modo actual si Realtime falla.
- Manejo de red lenta, intermitente o caida.
- Boton visible para salir completamente de la aplicacion.
- Continuidad de la sesion aunque el celular se bloquee.
- Voces claras, neutras y didacticas para aprendizaje de ingles.

### Se evalua despues

- WebRTC Audio Processing para cancelacion de eco, supresion de ruido y control automatico de ganancia.
- Ajustes mas finos para uso en carro cuando el audio salga por parlantes y pueda volver al microfono.

### Se deja para una fase posterior si realmente hace falta

- Oboe.
- Silero VAD local.
- RNNoise.

Estas herramientas son utiles, pero aumentan bastante la complejidad. Solo se agregaran si las pruebas demuestran que el sistema realtime basico no logra buena calidad en carro.

## Herramientas

### OpenAI Realtime API

Uso principal:

- Speech-to-Text.
- IA conversacional.
- Text-to-Speech.
- Respuestas parciales.
- Interrupciones naturales.
- Conversacion bidireccional de baja latencia.

Nota:

- Es la unica pieza de este plan que implica costo por uso.
- La API key nunca debe ir dentro de la aplicacion Android.
- El backend debe crear sesiones temporales o tokens efimeros para que Android se conecte de forma segura.

### AudioTrack

Uso principal:

- Reproducir audio del asistente en cuanto llegue.
- Cortar la voz del asistente rapidamente si el usuario interrumpe.
- Reducir la latencia frente al TTS actual del telefono.

### OkHttp WebSocket o WebRTC

Uso principal:

- Mantener una conexion realtime entre Android y el servicio de IA.
- Enviar audio del microfono en streaming.
- Recibir eventos de texto/audio/respuesta.

Decision tecnica:

- Se debe revisar la documentacion oficial vigente de OpenAI antes de implementar.
- Si OpenAI recomienda WebRTC para clientes moviles, se preferira WebRTC.
- Si WebSocket permite una implementacion Android mas simple y estable para esta version, se puede iniciar con WebSocket como experimento controlado.

### Kotlin Coroutines + Flow

Uso principal:

- Manejar captura de audio, envio, recepcion de eventos, reproduccion y estados de UI sin bloquear la app.
- Mantener un flujo claro de eventos: listening, thinking, speaking, paused, reconnecting, offline y error.

### WebRTC Audio Processing

Uso eventual:

- Cancelacion de eco.
- Supresion de ruido.
- Control automatico de ganancia.

Se agregara solo si el asistente se escucha a si mismo, si hay eco fuerte en el carro o si la calidad del audio con datos moviles/ruido real no es suficiente.

### Oboe, Silero VAD y RNNoise

Uso eventual:

- Oboe: captura de audio de muy baja latencia.
- Silero VAD: deteccion local avanzada de voz y silencio.
- RNNoise: reduccion de ruido local.

No son parte de la primera implementacion realtime. Se documentan como mejoras futuras para evitar sobrecargar la primera version.

## Comportamiento esperado

### Conversacion natural

- El usuario inicia la conversacion.
- El microfono permanece activo durante la sesion.
- El asistente responde con audio en streaming.
- El usuario puede interrumpir al asistente hablando.
- La aplicacion corta la voz del asistente y atiende lo nuevo que dijo el usuario.
- La conversacion continua sin que el usuario tenga que tocar la pantalla.
- La conversacion debe seguir activa aunque el celular este bloqueado, siempre que el usuario no finalice la conversacion ni cierre la aplicacion.

### Salida completa de la aplicacion

La interfaz principal debe tener un boton para salir completamente de la aplicacion.

Comportamiento esperado:

- detener microfono;
- detener reproduccion de audio;
- cerrar sesion realtime si esta activa;
- detener servicio en foreground;
- limpiar estados temporales de conversacion;
- salir de la pantalla principal de forma segura.

Este boton no reemplaza el comando de voz para cerrar la aplicacion. Ambos deben coexistir.

### Comandos de voz

Los comandos actuales se conservan:

- pausar;
- continuar;
- finalizar conversacion;
- cerrar aplicacion.

El comando de finalizar debe despedirse con una frase corta antes de cerrar la conversacion.

### Correcciones de ingles

Se conserva la configuracion de nivel de correccion:

- bajo: solo correcciones importantes;
- medio: correcciones utiles sin interrumpir demasiado;
- alto: correcciones frecuentes cuando haya oportunidad de mejora.

El historial de feedback se conserva y solo debe guardar correcciones reales, no entradas donde no hubo correccion.

### Asistentes y voces

Se conservan los asistentes configurables.

Con Realtime, las voces probablemente pasaran a depender de las voces disponibles en OpenAI. Se debe mapear cada asistente a una voz diferenciada:

- mujer joven;
- mujer mayor;
- hombre joven;
- hombre mayor.

Si Realtime no esta disponible, se usa el TTS actual como fallback.

La seleccion de voces debe priorizar claridad y pronunciacion pedagogica por encima de voces demasiado dramaticas, rapidas o expresivas. El objetivo es que un usuario que esta aprendiendo ingles pueda entender con facilidad.

Recomendacion para voces:

- usar voces con acento americano claro y neutro;
- evitar voces con velocidad natural demasiado alta;
- evitar voces con efectos, exageraciones o entonaciones poco didacticas;
- diferenciar los cuatro asistentes por tono, edad percibida y genero, sin sacrificar inteligibilidad;
- permitir vista previa de voz al seleccionar asistente.

### Ritmo de habla

No se implementara una barra de velocidad de habla en esta fase.

Decision:

- se prioriza una voz clara, neutra y didactica;
- el asistente debe hablar con ritmo moderado por defecto;
- el ritmo debe controlarse mediante la seleccion de voces y las instrucciones del asistente;
- se evita manipular artificialmente la velocidad si eso reduce naturalidad o calidad de pronunciacion;
- una opcion avanzada de velocidad podria evaluarse en el futuro si las pruebas lo justifican.

## Manejo de internet lento o deficiente

La aplicacion se usara principalmente con datos moviles. Por eso debe asumir que durante un trayecto puede haber zonas con mala senal, latencia alta o perdida temporal de conexion.

### Estados de red

La app debe manejar estos estados:

- **Online**: conexion normal.
- **Slow network**: la IA tarda mas de lo esperado.
- **Reconnecting**: se perdio la conexion realtime y se intenta recuperar.
- **Offline**: no hay conexion usable.
- **Fallback mode**: se desactiva Realtime y se usa el modo actual si es posible.

### Comportamiento con red lenta

Si la red se pone lenta:

- la app no debe cerrarse;
- no debe mostrar un error agresivo de inmediato;
- debe indicar que esta reconectando o esperando red;
- debe mantener la conversacion en pausa tecnica;
- debe evitar enviar muchas solicitudes duplicadas;
- debe conservar el contexto local suficiente para continuar cuando vuelva la conexion.

### Timeouts recomendados

- Si no hay respuesta realtime en pocos segundos, mostrar estado "Reconnecting" o equivalente.
- Si la conexion no vuelve despues de un limite razonable, pasar a fallback o pausar la conversacion.
- Si vuelve internet, reanudar la sesion o crear una sesion nueva sin obligar al usuario a reiniciar la app.

### Fallback

Si Realtime falla:

1. La app debe intentar reconectar.
2. Si no puede, debe cambiar al modo actual estable.
3. Si tampoco puede usar el backend, debe avisar de forma clara y corta.
4. Cuando la red vuelva, puede ofrecer o ejecutar reconexion al modo realtime.

El objetivo es que una falla de internet no rompa la experiencia completa.

## Fases de desarrollo

### Fase 1: Preparacion segura

- Crear rama experimental.
- Mantener el modo actual como fallback.
- Agregar configuracion interna para activar/desactivar modo realtime.
- Revisar documentacion oficial vigente de OpenAI Realtime.
- Definir modelo realtime inicial.
- Definir voces disponibles y mapeo a asistentes.
- Definir politica de voces claras para aprendizaje de ingles.
- Confirmar que la sesion sigue funcionando con pantalla bloqueada.

Resultado esperado:

- La app sigue funcionando igual que hoy.
- Existe una base para activar el modo realtime sin perder estabilidad.

### Fase 2: Backend Realtime

- Agregar endpoint backend para crear sesiones realtime seguras.
- No exponer la API key en Android.
- Validar modelo permitido.
- Manejar errores de OpenAI de forma clara.
- Agregar logs utiles sin filtrar secretos.

Resultado esperado:

- Android puede pedir una sesion temporal al backend.
- El backend controla seguridad, modelos y errores.

### Fase 3: Cliente Android Realtime

- Crear cliente realtime separado del flujo actual.
- Enviar audio del microfono en streaming.
- Recibir eventos de respuesta.
- Reproducir audio con AudioTrack.
- Cortar respuesta en curso cuando el usuario interrumpe.
- Mantener estados claros en UI.
- Usar voces claras con ritmo moderado para aprendizaje de ingles.
- Mantener la sesion activa con pantalla bloqueada mediante el servicio en foreground.
- Implementar boton para salir completamente de la aplicacion.

Resultado esperado:

- Primera conversacion realtime funcional.
- El usuario puede interrumpir al asistente.
- El modo actual sigue disponible como fallback.

### Fase 4: Robustez en datos moviles

- Detectar red lenta o caida.
- Agregar reconexion controlada.
- Evitar duplicar mensajes al reconectar.
- Pausar y reanudar sesion cuando la red vuelva.
- Probar con Wi-Fi, datos moviles y perdida temporal de conexion.

Resultado esperado:

- La app no se rompe cuando el internet se pone lento.
- El usuario recibe estados claros y la conversacion puede continuar.

### Fase 5: Ajuste fino de experiencia

- Medir latencia real.
- Ajustar sensibilidad de interrupciones.
- Ajustar voces por asistente.
- Validar uso en Android Auto.
- Evaluar si hace falta WebRTC Audio Processing.

Resultado esperado:

- Experiencia mas natural y estable.
- Decision informada sobre agregar o no procesamiento avanzado de audio.

## Riesgos principales

- Mayor consumo de datos moviles.
- Mayor costo de OpenAI frente al modo actual.
- Latencia variable por cobertura movil.
- Eco si el audio del asistente sale por parlantes del carro.
- Mayor complejidad tecnica si se agrega WebRTC, Oboe, Silero o RNNoise demasiado pronto.

## Recomendacion final

No implementar todo el documento original de una sola vez. La mejor ruta es empezar con el corazon de la mejora:

```text
OpenAI Realtime API
+ streaming de audio
+ AudioTrack
+ interrupciones naturales
+ backend seguro
+ fallback por red
```

Despues de probar eso en el celular y en el carro, se decide si hace falta sumar WebRTC Audio Processing, Oboe, Silero VAD o RNNoise.
