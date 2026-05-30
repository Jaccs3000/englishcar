# Android Auto American English Voice Coach - Official Product Specification v2

## 1. Objetivo

Crear una aplicacion Android moderna, rapida y premium para practicar American English mientras se conduce.

La experiencia debe sentirse como un copiloto conversacional de American English:

- Natural.
- Rapida.
- Fluida.
- Conversacional.
- Casi completamente hands-free.
- Comoda para conducir.
- Visualmente premium.
- Poco invasiva.

La aplicacion debe permitir que el usuario mantenga conversaciones naturales en ingles americano, reciba correcciones utiles cuando sea necesario, escuche sugerencias mas nativas y continue conversando sin sentir que esta en una clase formal.

---

## 2. Principio central

La app debe conversar primero y ensenar despues.

No debe sentirse como:

- Una clase de ingles.
- Una app educativa pesada.
- Un chatbot generico.
- Una app Android basica.
- Una demo experimental.
- Una plataforma enterprise.
- Duolingo.
- Un examen.

Debe sentirse como:

- Un acompanante conversacional.
- Un coach discreto.
- Un copiloto de practica oral.
- Una experiencia premium de voz.

---

## 3. Alcance del MVP

### El MVP incluye

1. Aplicacion Android nativa.
2. Backend propio para comunicacion segura con OpenAI.
3. Conversaciones por voz.
4. SpeechRecognizer.
5. Google TTS.
6. Android Auto.
7. Streaming de respuestas de IA desde backend.
8. Correcciones gramaticales.
9. Sugerencias de expresiones mas naturales.
10. Historial local de correcciones.
11. Configuracion simple.
12. Cinco asistentes configurables.
13. Nombres editables para asistentes.
14. Un solo asistente activo a la vez.
15. Comandos personalizados.
16. Manejo de interrupciones.
17. Continuidad conversacional.
18. Feedback posterior.
19. Persistencia local de preferencias.

### El MVP no incluye

- Login.
- Multiusuario.
- Cloud sync.
- Firebase.
- Gamificacion.
- Lessons.
- Quizzes.
- Analytics.
- WearOS.
- Widgets.
- Video.
- Audio persistente.
- Funciones sociales.
- IA offline.
- Filtros avanzados.
- Navegacion compleja.
- Dashboard.
- Panel administrativo.

---

## 4. Arquitectura general

La solucion tendra dos partes oficiales:

1. Aplicacion Android.
2. Backend propio.

El backend existe para proteger la API key de OpenAI, controlar modelos, centralizar prompts, aplicar limites basicos y permitir streaming seguro hacia Android.

La app Android conserva la experiencia principal: voz, UI, estado conversacional, TTS, SpeechRecognizer, Android Auto, preferencias locales y feedback local.

### Uso esperado

La aplicacion es de uso personal.

No sera publicada en Google Play Store en esta version.

El usuario la usara siempre desde su telefono personal, principalmente con datos moviles y ocasionalmente con Wi-Fi.

La app debe funcionar correctamente con red movil, tolerar latencia variable y recuperarse de cortes breves de conexion.

### Backend en esta version

El backend debe estar preparado para ejecutarse como servicio remoto simple, accesible por internet desde el celular.

Para evitar mantenimiento pesado, no debe requerir:

- Base de datos remota.
- Panel administrativo.
- Sistema de usuarios.
- Jobs programados.
- Infraestructura compleja.

El backend debe poder correr localmente durante desarrollo con Wrangler y desplegarse despues en Cloudflare Workers con HTTPS automatico.

### Regla de simplicidad

Si existe una solucion simple y una compleja, elegir siempre la simple.

No crear:

- Microservicios.
- Arquitectura distribuida.
- Sistemas enterprise.
- Capas redundantes.
- Managers duplicados.
- Abstracciones innecesarias.

---

## 5. Stack Android obligatorio

| Area | Tecnologia |
| --- | --- |
| Lenguaje | Kotlin |
| UI | Jetpack Compose |
| Arquitectura | MVVM simple |
| DI | Hilt |
| Async | Coroutines + Flow |
| Persistencia local | Room |
| Preferencias | DataStore |
| Voz | SpeechRecognizer |
| TTS | Google TTS |
| Red | Retrofit u OkHttp |
| Android Auto | Android for Cars App Library |

### Estructura Android recomendada

```text
app/
audio/
ai/
conversation/
settings/
history/
assistant/
auto/
ui/
```

Mantener arquitectura simple y facil de seguir.

---

## 6. Stack backend obligatorio

Backend pequeno, bien estructurado y centrado en OpenAI.

Proveedor oficial:

```text
Cloudflare Workers Free
```

Motivo:

- Cero mantenimiento de servidor.
- HTTPS automatico.
- Buen rendimiento global.
- Sin cold starts pesados para este caso de uso.
- Free tier suficiente para uso personal.
- Adecuado para proxy ligero hacia OpenAI.

| Area | Tecnologia |
| --- | --- |
| Proveedor | Cloudflare Workers Free |
| Lenguaje | TypeScript |
| Framework | Hono |
| Validacion | Zod |
| HTTP streaming | Server-Sent Events |
| Configuracion | Cloudflare secrets / environment variables |
| Seguridad | HTTPS automatico + APP_API_TOKEN |
| IA | OpenAI API desde backend |

### Proveedor IA

En esta version se usara solamente OpenAI.

No integrar:

- Anthropic.
- Google Gemini.
- Azure OpenAI.
- Proveedores locales.
- Fallback multi-proveedor.

### Responsabilidades del backend

El backend debe:

- Guardar y usar la API key de OpenAI.
- Exponer un endpoint de conversacion con streaming.
- Validar el modelo solicitado contra una lista permitida.
- Construir el prompt oficial.
- Recibir contexto reciente enviado por Android.
- Exponer modelos disponibles a Android desde una allowlist.
- Devolver respuesta conversacional.
- Devolver correccion, sugerencia natural y explicacion breve cuando aplique.
- No guardar audio.
- No guardar conversaciones completas por defecto.
- No exponer la API key al cliente.
- Proteger endpoints con token simple para uso personal.
- Ejecutarse en Cloudflare Workers.

### El backend no debe

- Manejar UI.
- Guardar audio.
- Crear dashboard.
- Crear sistema de usuarios en el MVP.
- Hacer cloud sync.
- Reemplazar la logica local de comandos.
- Reemplazar el estado conversacional local de Android.
- Implementar multiusuario.
- Implementar autenticacion compleja.
- Requerir servidor propio.
- Requerir VPS.
- Requerir contenedores.
- Requerir Fastify o Express.

---

## 7. Seguridad

### Obligatorio

- La API key de OpenAI vive solo en backend.
- Android nunca contiene API keys de OpenAI.
- HTTPS obligatorio en produccion.
- Android debe autenticar llamadas al backend usando un token privado de app.
- No loguear texto sensible innecesario.
- No guardar audio.
- No almacenar conversaciones completas.
- Validar entradas desde Android.
- Permitir solo modelos definidos en allowlist.

### Autenticacion personal app-backend

Como la app es de uso personal y no se publicara en Play Store, no habra login ni cuentas.

El backend debe usar un token simple:

```text
APP_API_TOKEN=...
```

Android enviara:

```text
Authorization: Bearer <APP_API_TOKEN>
```

Esta proteccion es suficiente para la version personal.

No implementar en el MVP:

- OAuth.
- Firebase Auth.
- Registro de usuarios.
- Refresh tokens.
- Roles.

### Logs backend

El backend puede guardar logs tecnicos minimos:

- Request ID.
- Timestamp.
- Modelo usado.
- Duracion.
- Status code.
- Tipo de error si existe.

El backend no debe guardar:

- Texto completo del usuario.
- Respuestas completas de IA.
- Prompts completos.
- Audio.
- Historial conversacional.

### MVP local

En desarrollo se puede usar backend local con variables de entorno.

Ejemplo:

```text
OPENAI_API_KEY=...
APP_API_TOKEN=...
PORT=...
```

### Despliegue personal

Para uso real con datos moviles, el backend debe estar disponible por internet con HTTPS.

El backend se desplegara en Cloudflare Workers.

No usar VPS ni servicios que duerman instancias en free tier.

La URL del Worker debe ser configurable en Android.

El backend debe usar Cloudflare secrets para:

```text
OPENAI_API_KEY
APP_API_TOKEN
```

---

## 8. American English oficial

Toda la experiencia debe usar American English `en-US`.

Esto aplica a:

- SpeechRecognizer.
- Google TTS.
- Prompts.
- Correcciones.
- Pronunciacion.
- Expresiones.
- Vocabulario.
- Asistentes.

Ejemplos preferidos:

- apartment
- elevator
- truck
- vacation

Evitar:

- flat
- lift
- lorry
- holiday

---

## 9. Flujo principal oficial

1. Usuario abre la app.
2. Usuario presiona PLAY.
3. Android llama al backend con `start_conversation`.
4. La IA inicia conversacion con un saludo breve.
5. SpeechRecognizer escucha.
6. Usuario habla.
7. Android detecta final de habla usando el silence timeout configurado.
8. Android procesa comandos locales primero.
9. Si no es comando, Android envia texto, configuracion y contexto reciente al backend.
10. Backend llama a OpenAI con streaming.
11. Backend devuelve respuesta estructurada y texto conversacional.
12. Android reproduce la respuesta con Google TTS.
13. Android guarda feedback local si hubo correccion o sugerencia util.
14. Android vuelve automaticamente a Listening.

La conversacion debe sentirse:

- Natural.
- Rapida.
- Continua.
- Fluida.
- Humana.

---

## 10. Estados de conversacion

Estados permitidos:

```text
Idle
Listening
WaitingAI
Speaking
Paused
Interrupted
Error
```

Reglas:

- Solo puede existir un estado activo a la vez.
- El estado debe exponerse como una unica fuente de verdad.
- Android controla la maquina de estados.
- El backend no controla el estado de UI.

---

## 11. Asistentes

La aplicacion tendra exactamente cuatro asistentes.

Cada asistente tiene:

- ID interno fijo.
- Nombre por defecto.
- Nombre editable por el usuario.
- Voz masculina o femenina.
- Personalidad ligera.
- Preview individual.
- Configuracion persistente local.

Solo un asistente puede estar activo al mismo tiempo.

El usuario puede cambiar entre asistentes desde Settings.

La aplicacion debe entender y usar el nombre configurado por el usuario para el asistente activo.

### Asistentes oficiales

| ID | Nombre por defecto | Voz | Personalidad |
| --- | --- | --- | --- |
| emma | Emma | Female | Young |
| sophia | Sophia | Female | Mature |
| alex | Alex | Male | Young |
| james | James | Male | Mature |

### Preview de voz

Cada asistente tendra un boton individual de preview.

Si el usuario reproduce otro preview, el preview anterior debe detenerse automaticamente.

Frase oficial de demostracion:

```text
Hi, my name is Emma. I'm your virtual assistant and I'll help you practice natural American English conversations. It's great to meet you.
```

El nombre debe reemplazarse dinamicamente por el nombre configurado por el usuario.

### Restriccion visual

La seleccion de asistentes no debe verse como:

- Spinner Android generico.
- Texto plano aburrido.

Debe verse:

- Moderna.
- Visual.
- Premium.
- Tecnologica.

---

## 12. Nombre del usuario

El usuario puede configurar su nombre.

La IA puede mencionarlo ocasionalmente para hacer la conversacion mas natural.

Ejemplos:

```text
Good job, Michael.
That sounds great, Michael.
```

Regla importante:

- La IA no debe repetir constantemente el nombre del usuario.

---

## 13. Modelos de IA

La aplicacion debe ofrecer tres modelos oficiales vigentes definidos por el proyecto.

La lista debe vivir en backend como allowlist y Android solo debe mostrar esos modelos.

Lista oficial inicial:

```text
gpt-5.2
gpt-5-mini
gpt-5-nano
```

Modelo por defecto recomendado:

```text
gpt-5-mini
```

Roles:

- `gpt-5.2`: modelo optimo de mayor calidad para respuestas mas precisas.
- `gpt-5-mini`: modelo por defecto, balance entre calidad, velocidad y coste.
- `gpt-5-nano`: modelo de bajo consumo para ahorrar tokens/coste en conversaciones simples.

Restricciones:

- No permitir modelos custom desde Android.
- No exponer parametros tecnicos avanzados.
- No permitir temperatura, top_p u otros ajustes al usuario final.
- Usar Responses API en backend.
- Usar razonamiento bajo o ninguno cuando sea posible para reducir latencia.
- Revisar la allowlist si OpenAI depreca modelos.

---

## 14. Contrato Android-backend

El backend debe exponer un endpoint principal de conversacion.

Endpoint conceptual:

```text
POST /v1/conversation/stream
```

Headers:

```text
Authorization: Bearer <APP_API_TOKEN>
Content-Type: application/json
Accept: text/event-stream
```

Request conceptual:

```json
{
  "requestId": "uuid",
  "type": "conversation_turn",
  "userText": "I go yesterday",
  "assistantId": "emma",
  "assistantName": "Emma",
  "assistantPersonality": "Warm",
  "userName": "Michael",
  "model": "gpt-5-mini",
  "locale": "en-US",
  "recentContext": [
    {
      "role": "user",
      "text": "I went to Miami."
    },
    {
      "role": "assistant",
      "text": "Nice. What did you like most about Miami?"
    }
  ]
}
```

Tipos permitidos:

```text
start_conversation
conversation_turn
```

Para `start_conversation`, `userText` puede ser null o vacio.

### Contexto conversacional

Android mantiene el contexto reciente en memoria.

Reglas:

- Mantener las ultimas 12 interacciones por defecto.
- Maximo interno: 20 interacciones.
- No guardar contexto como historial permanente.
- Enviar al backend solo el contexto necesario para continuidad.
- Limpiar contexto con Finish.
- Mantener contexto con Pause y Resume.

---

## 15. Streaming y respuesta de IA

El backend debe devolver una respuesta estructurada para que Android no tenga que adivinar.

Para el MVP, Android esperara el evento final antes de reproducir TTS.

El streaming se usara para mantener la conexion viva, permitir UI reactiva y preparar mejoras futuras.

Protocolo recomendado:

```text
event: delta
data: {"text":"Small improvement: "}

event: delta
data: {"text":"say 'I went yesterday.' "}

event: final
data: {"spokenReply":"Small improvement: say 'I went yesterday.' What did you do after work?","correction":"I went yesterday.","naturalAlternative":"I went yesterday.","shortExplanation":"Use past tense for yesterday.","shouldSaveFeedback":true}
```

Formato final:

```json
{
  "spokenReply": "Small improvement: say 'I went yesterday.' What did you do after work?",
  "correction": "I went yesterday.",
  "naturalAlternative": "I went yesterday.",
  "shortExplanation": "Use past tense for yesterday.",
  "shouldSaveFeedback": true
}
```

Reglas:

- `spokenReply` es lo que Android lee con TTS.
- `correction` puede ser null si no aplica.
- `naturalAlternative` puede existir aunque la frase sea gramaticalmente correcta.
- `shortExplanation` debe ser una frase corta.
- `shouldSaveFeedback` indica si Room debe guardar la entrada.
- Si ocurre un error, backend debe enviar evento `error` o responder HTTP error.
- Android no debe intentar parsear correcciones desde texto libre.

---

## 16. Correcciones y naturalidad

La IA debe corregir al usuario cuando sea necesario.

Tambien debe intervenir cuando la frase sea correcta pero no suene natural o nativa.

La intervencion ocurre al final de la intervencion del usuario, antes de continuar la conversacion.

Orden recomendado:

1. Correccion breve si aplica.
2. Alternativa mas natural si aplica.
3. Continuacion de la conversacion.

Ejemplo con error:

```text
User: I go yesterday.
Assistant: Small improvement: say "I went yesterday." What did you do after work?
```

Ejemplo con frase correcta pero poco natural:

```text
User: I am very happy today.
Assistant: That's correct. A more natural way to say it is "I'm feeling great today." What made your day so good?
```

Reglas:

- Correcciones cortas.
- Tono conversacional.
- No invasivo.
- No romper el flujo.
- No corregir cada detalle minimo.
- Priorizar errores importantes, naturalidad y fluidez.
- Maximo una explicacion breve.
- Evitar monologos largos.

---

## 17. Comandos personalizados

El usuario podra configurar palabras o frases para estas acciones:

- Start.
- Pause.
- Resume.
- Finish.

Ejemplo:

```text
Pause Command: Hold on
```

Reglas:

- Los comandos se procesan localmente en Android.
- Se procesan antes de llamar al backend.
- Tienen prioridad maxima.
- Matching case-insensitive.
- Ignorar espacios extra.
- Evitar activaciones accidentales por coincidencias parciales.
- Preferir coincidencia completa o muy clara.

### Acciones

`Start`:

- Inicia la conversacion desde Idle.

`Pause`:

- Pausa temporalmente.
- Mantiene contexto reciente.
- Espera Resume.

`Resume`:

- Reanuda conversacion.
- Mantiene continuidad.

`Finish`:

- Detiene TTS y SpeechRecognizer.
- Termina la sesion.
- Limpia contexto conversacional.
- Vuelve a Home.

Nota:

- No habra comando separado `Stop` en el MVP para evitar ambiguedad.
- Si la UI muestra un boton de detener, ejecutara `Finish`.

---

## 18. Silence timeout

El tiempo de silencio se configura en milisegundos.

Este timeout indica cuanto espera Android despues de que el usuario deja de hablar antes de enviar el texto al backend.

Opciones recomendadas:

```text
500 ms a 5000 ms
Incrementos de 100 ms
```

Valor por defecto:

```text
3500 ms
```

Objetivo:

- Usuarios rapidos tienen menor latencia.
- Usuarios pausados tienen mas comodidad.
- La conversacion se siente natural.
- La UI debe mostrar este valor en segundos con un decimal, por ejemplo `1.6s`.
- El maximo de este control es 5 segundos; no confundir con Auto pause, que puede llegar a 5 minutos.

### Auto pausa por inactividad

Separado del silence timeout.

El usuario podra configurar este tiempo desde Settings.

Opciones oficiales:

```text
Off
30000 ms
60000 ms
120000 ms
300000 ms
```

Valor por defecto:

```text
60000 ms
```

Despues del tiempo configurado sin interaccion:

La app debe:

- Entrar automaticamente en Paused.
- Decir: `Conversation paused.`
- Si esta en Off, no debe auto pausarse por inactividad.

---

## 19. Persistencia local

Android debe recordar automaticamente:

- Asistente activo.
- Nombres personalizados de los cuatro asistentes.
- Nombre del usuario.
- Modelo IA elegido.
- Nivel de correccion elegido.
- Comandos personalizados.
- Silence timeout en milisegundos.
- Auto pausa por inactividad en milisegundos.

Usar DataStore para preferencias.

Usar Room para historial de feedback.

No guardar:

- Audio.
- Conversaciones completas.
- Transcripciones completas de sesion.

---

## 20. Historial y feedback posterior

Durante la conversacion no mostrar feedback grande ni paneles invasivos.

Despues, el usuario podra revisar feedback en FeedbackScreen.

Guardar solo entradas utiles:

- Texto del usuario.
- Correccion.
- Alternativa natural.
- Explicacion breve.
- Timestamp.
- Assistant ID.
- Modelo usado.

Estructura esperada:

```text
Original Phrase
Corrected Phrase
Natural Alternative
Short Explanation
Timestamp
```

FeedbackScreen debe ser:

- Simple.
- Ligera.
- Sin estadisticas.
- Sin ejercicios.
- Sin quizzes.
- Debe permitir buscar feedback guardado por frase original, correccion, alternativa natural, explicacion, asistente o modelo.
- Debe mostrar contador de correcciones guardadas y resultados filtrados cuando exista busqueda.

---

## 21. Pantallas oficiales

Pantallas permitidas:

- FirstLaunchSetupScreen.
- HomeScreen.
- ConversationScreen.
- SettingsScreen.
- FeedbackScreen.

No crear:

- Navegacion profunda.
- Tabs complejos.
- Menus secundarios.
- Pantallas extra.
- Dashboards.

---

## 22. First launch

En el primer uso mostrar setup rapido.

Debe pedir:

- Nombre del usuario.
- Elegir asistente.
- Continue.

Duracion objetivo:

```text
menos de 30 segundos
```

Default:

- Assistant: Emma.
- Voice: Female.
- Model: gpt-5-mini.
- Silence timeout: 1500 ms.

---

## 23. HomeScreen

La HomeScreen gira alrededor del boton PLAY.

No es:

- Dashboard.
- Menu complejo.
- Feed.
- Historial.

Es:

```text
PLAY CONVERSATION
```

Layout conceptual:

```text
--------------------------------
|                              |
|      Animated Voice Wave     |
|                              |
|                              |
|          [ PLAY ]            |
|                              |
|                              |
|      Assistant Ready         |
|                              |
|             Settings         |
--------------------------------
```

El boton PLAY debe ser:

- Grande.
- Central.
- Principal foco visual.
- Premium.
- Facil de presionar conduciendo.

Mientras haya conversacion activa:

- Ocultar PLAY o convertirlo en Finish.

---

## 24. ConversationScreen

Debe sentirse:

- Viva.
- Reactiva.
- Conversacional.
- Ligera.

### Listening

Mostrar:

- Ondas activas.
- Glow suave.
- Indicador claro de microfono activo.
- Feedback inmediato.

### WaitingAI

Mostrar:

```text
Thinking...
```

Sin loaders complejos.

### Speaking

Mostrar ondas reaccionando al audio o una animacion ligera.

### Paused

Mostrar estado visual claro.

### Error

Mostrar:

```text
Something went wrong
```

Con acciones:

- Retry.
- Back Home.

### Correcciones

Las correcciones deben verse:

- Pequenas.
- Elegantes.
- No invasivas.
- Secundarias frente a la conversacion.

---

## 25. SettingsScreen

Toda la configuracion debe poder hacerse en menos de 1 minuto.

Componentes:

- Assistant selector.
- Assistant preview.
- Assistant name editor.
- User name.
- AI model selector.
- Correction level selector.
- Silence timeout selector en milisegundos.
- Custom commands.

Restricciones:

- No usar spinners genericos si se puede usar un control visual mas cuidado.
- No mostrar parametros tecnicos avanzados.
- No saturar la pantalla.

---

## 26. Filosofia visual

La aplicacion debe verse:

- Moderna.
- Premium.
- Tecnologica.
- Fluida.
- Elegante.
- Viva.

Usar:

- Dark theme.
- Gradientes suaves.
- Glow ligero.
- Ondas animadas.
- Transiciones suaves.
- Excelente legibilidad.

No usar:

- UI generica Android.
- Diseno infantil.
- Saturacion excesiva.
- Animaciones pesadas.
- Layouts cargados.
- Dashboard visual.

Las animaciones deben ser:

- Ligeras.
- Fluidas.
- Rapidas.
- Sin afectar performance.

---

## 27. Arquitectura de audio

Pipeline oficial:

```text
Microphone
-> AudioRecord continuous capture
-> ConversationManager
-> Backend
-> OpenAI
-> Backend streaming
-> ConversationManager
-> Google TTS
-> Speaker
```

Reglas:

- La conversacion debe funcionar desde el telefono o Android Auto.
- Durante TTS, pausar SpeechRecognizer.
- Nunca escuchar mientras TTS habla.
- Evitar que la app escuche su propia voz.
- Si el usuario interrumpe mientras TTS habla, detener TTS y volver a Listening.

### Continuous speech recognition

Reglas:

- Pedir permiso de microfono al pulsar PLAY si todavia no existe.
- En esta primera version se asume que el usuario concede el permiso de microfono.
- Si el permiso se deniega, mostrar solo un mensaje simple. No crear flujo avanzado hacia Settings del sistema en el MVP.
- Mantener el microfono abierto con `AudioRecord` mientras la app esta en Listening.
- Detectar fin de frase por silencio local usando el silence timeout configurado.
- Enviar el audio capturado al backend para transcripcion.
- No reiniciar el microfono cada pocos segundos durante espera silenciosa.
- Usar `en-US`.
- Si no hay frase util, seguir escuchando sin convertirlo en error fatal.

### Mejora futura: barge-in

En una fase posterior se podra permitir que el usuario hable mientras el asistente esta hablando.

Objetivo:

- Mantener el microfono activo durante TTS.
- Permitir interrumpir al asistente por voz.
- Permitir comandos como Pause, Finish o Close app mientras el asistente habla.
- Permitir que el usuario complete una idea si el asistente empezo a responder demasiado pronto.

Riesgos a resolver antes de implementarlo:

- El microfono puede captar la propia voz del asistente por los parlantes.
- La app podria transcribir la respuesta del asistente como si fuera del usuario.
- Se requiere mitigacion con cancelacion de eco, deteccion de similitud contra el ultimo texto hablado y reglas especiales durante Speaking.

Estado:

- No implementado en esta version.
- Considerarlo despues de estabilizar la captura continua, latencia y uso con Android Auto.

### Google TTS

Reglas:

- Usar `Locale.US`.
- Intentar seleccionar una voz del genero del asistente si el dispositivo la ofrece.
- Si no existe voz exacta, usar la mejor voz `en-US` disponible.
- No prometer voces identicas en todos los dispositivos.
- En MVP, esperar el evento `final` del backend antes de reproducir TTS.
- No reproducir TTS por chunks en la primera version.

---

## 28. Android Auto

Android Auto debe permitir practicar ingles mientras se conduce.

La interfaz Android Auto debe ser:

- Mas simple.
- Mas limpia.
- Menos visual.
- Menos distractora.

Android Auto no es el nucleo del sistema.

La conversacion debe continuar:

- Si Android Auto falla.
- Si se desconecta.
- Si se conecta despues.
- Si vuelve al telefono.

Indicadores simples:

```text
Android Auto Connected
Switched to Phone Audio
```

Acciones recomendadas:

- Start.
- Pause/Resume.
- Finish.

No incluir en Android Auto MVP:

- Settings.
- FeedbackScreen.
- Transcripciones largas.
- Correcciones extensas en pantalla.
- Configuracion de asistentes.

---

## 29. Interrupciones y continuidad

La app debe manejar:

- Llamadas.
- WhatsApp.
- Notificaciones.
- Audio focus changes.
- Background.
- Foreground.
- Android Auto disconnect.
- Android Auto reconnect.
- Bluetooth reconnect.

Regla:

- La conversacion debe continuar por donde iba cuando sea razonable.

Despues de una interrupcion, la app debe decir:

```text
We can continue whenever you're ready.
```

Y volver automaticamente a Listening cuando corresponda.

---

## 30. Errores

Si la IA no entiende:

```text
Sorry, I didn't understand that. Could you repeat it?
```

Si se pierde internet:

```text
Internet connection lost.
```

Si SpeechRecognizer falla:

- Reiniciar automaticamente.
- Maximo 3 retries consecutivos.
- Luego entrar en Error.

Si el backend falla:

- Mostrar estado Error.
- Permitir Retry.
- Mantener la app estable.
- Diferenciar errores principales para que el usuario sepa que ocurre:
  - `Internet connection lost.`
  - `AI response timed out.`
  - `Backend token is not valid.`
  - `AI backend is temporarily unavailable.`
- Si falla una respuesta de IA durante la conversacion, la app debe decir una frase breve de recuperacion y permitir intentar de nuevo sin romper la sesion.

---

## 31. Performance

La velocidad es critica.

La app debe sentirse:

- Inmediata.
- Fluida.
- Reactiva.
- Rapida.

Prioridad:

1. Fluidez conversacional.
2. Baja latencia.
3. Rapidez percibida.
4. Estabilidad.
5. Calidad visual.

Objetivos:

| Metrica | Objetivo |
| --- | --- |
| AI latency | Menos de 2 segundos idealmente |
| Startup Android | Menos de 3 segundos |
| Memoria Android | Menos de 250 MB si es razonable |
| UI | Sin bloqueos perceptibles |

---

## 32. Testing obligatorio

Probar:

- Android Auto DHU.
- Dispositivo real.
- Ruido ambiente.
- Bluetooth reconnect.
- Android Auto reconnect.
- Llamadas.
- Interrupciones.
- Perdida de internet.
- Backend caido.
- SpeechRecognizer retries.
- TTS interrumpido.

La app debe tolerar:

- Ruido moderado.
- Conduccion normal.
- Audio imperfecto.
- Latencia variable de red.

---

## 33. Fases oficiales

### Fase 1 - Foundation Android + Backend

Incluye:

- Proyecto Android.
- Proyecto backend Cloudflare Workers.
- Compose.
- Hilt.
- Hono TypeScript.
- Health endpoint.
- Configuracion por entorno.
- Cloudflare secrets.
- DataStore.
- Navegacion simple.
- Estado base.

Resultado esperado:

- Android puede abrir.
- Backend responde health.
- Android puede leer configuracion local.
- Android puede configurar URL de backend y token.
- Backend rechaza requests sin token.
- Android carga desde backend la allowlist de modelos disponibles para seleccion del usuario.

### Fase 2 - Audio y conversacion dummy

Incluye:

- SpeechRecognizer.
- Google TTS.
- ConversationManager.
- State machine.
- TTS dummy.
- Manejo basico de permisos.

Resultado esperado:

- Usuario habla.
- App responde por TTS dummy.
- Estados funcionan.
- Permiso de microfono se maneja correctamente.
- TTS usa `Locale.US`.

### Fase 3 - OpenAI streaming via backend

Incluye:

- Endpoint de conversacion.
- Streaming backend.
- Allowlist de modelos.
- Prompt oficial.
- Contexto reciente.
- Integracion Android-backend.

Resultado esperado:

- Conversacion con IA real.
- Respuestas rapidas.
- API key protegida en backend.
- Android recibe eventos `delta`, `final` y `error`.
- Android reproduce TTS desde `spokenReply`.

### Fase 4 - Correcciones y feedback

Incluye:

- Respuesta estructurada.
- Correcciones.
- Sugerencias naturales.
- Room.
- FeedbackScreen.

Resultado esperado:

- Feedback inteligente funcional.
- Solo se guarda lo necesario.
- Room guarda solo entradas con `shouldSaveFeedback=true`.
- FeedbackScreen muestra historial local simple.

### Fase 5 - Android Auto, interrupciones y polish

Incluye:

- Android Auto.
- Audio focus.
- Bluetooth reconnect.
- Recovery.
- Pulido visual.
- QA.

Resultado esperado:

- App estable lista para beta.
- Android Auto muestra controles basicos.
- Interrupciones comunes no rompen la conversacion.

Estado implementado:

- Comandos locales configurables para Pause, Resume y Finish.
- Comando local configurable para cerrar completamente la app.
- Los comandos se procesan en Android antes de llamar al backend.
- HomeScreen permite Pause, Resume y Finish con controles simples.
- Text-to-speech de conversacion usa la voz/estilo del asistente activo.
- Manejo inicial de audio focus: ante perdida de foco, la conversacion pasa a Paused.
- Servicio Android Auto basico con controles Start, Pause/Resume y Finish.
- Android Auto preparado para deteccion como template app:
  - descriptor `automotive_app_desc`;
  - metadata `com.google.android.gms.car.application`;
  - pantalla de Android Auto se actualiza con cambios de estado de conversacion;
  - Start desde Android Auto inicia tambien el Foreground Service;
  - Finish desde Android Auto detiene tambien el Foreground Service.
- Migracion de reconocimiento de voz:
  - reemplazado `SpeechRecognizer` por captura continua `AudioRecord`;
  - agregado endpoint backend `/v1/transcribe`;
  - transcripcion con OpenAI `gpt-4o-mini-transcribe`;
  - Android envia WAV al backend con `Authorization: Bearer <APP_API_TOKEN>`;
  - el microfono permanece activo durante Listening hasta detectar frase, pausar, finalizar o salir.
- Pulido visual inicial de HomeScreen/ConversationScreen:
  - onda animada ligera;
  - estado central mas visible;
  - boton PLAY mas prominente;
  - ultimas frases en tarjetas compactas;
  - navegacion inferior mas limpia.
- Nivel de correccion configurable:
  - Low: solo correcciones importantes;
  - Medium: correcciones importantes y naturalidad util;
  - High: decir toda correccion util en voz alta.
- Filtro para no guardar feedback vacio o placeholders como `none`, `n/a`, `ninguna`.
- Limpieza automatica de feedback invalido anterior al abrir FeedbackScreen.
- Pruebas backend para normalizacion de feedback y niveles de correccion.
- Manejo inicial de interrupciones:
  - modo conduccion con Foreground Service tipo microphone;
  - al bloquear pantalla, la conversacion intenta seguir activa;
  - al iniciar conversacion, Android solicita audio focus;
  - si otra app, llamada o audio externo toma completamente el foco, la conversacion pasa a Paused con mensaje de continuidad;
  - perdidas transitorias de audio focus no deben pausar la app para evitar falsos positivos durante TTS;
  - el Foreground Service escucha cambios de ruta de audio mientras la pantalla esta bloqueada;
  - notificacion persistente mientras la sesion esta activa;
  - permiso POST_NOTIFICATIONS solicitado cuando Android lo requiere;
  - notificacion refleja estado Listening, Thinking, Speaking o Paused;
  - acciones Pause, Resume y Finish desde la notificacion;
  - accion Finish desde la notificacion;
  - cambio/desconexion de salida de audio pausa la conversacion.
- Finish por comando de voz reproduce despedida corta antes de cerrar la conversacion.
- Manejo inicial de errores de backend/red en Android:
  - internet no disponible;
  - timeout de respuesta;
  - token invalido;
  - backend temporalmente no disponible;
  - mensajes hablados breves para que el usuario entienda el problema durante la conversacion.
- Auto pausa por inactividad:
  - el usuario puede configurarla desde Settings;
  - opciones: Off, 30000 ms, 60000 ms, 120000 ms, 300000 ms;
  - si la app queda en Listening durante el tiempo configurado sin interaccion, pasa a Paused;
  - conserva el contexto reciente;
  - reproduce `Conversation paused.`;
  - no aplica mientras la app esta pensando, hablando o ya pausada;
  - ignora errores tardios del SpeechRecognizer causados por detener la escucha de forma intencional al pausar;
  - mientras esta en Paused, ningun error de SpeechRecognizer debe cambiar la UI a Error;
  - `ERROR_NO_MATCH` y `ERROR_SPEECH_TIMEOUT` durante espera silenciosa no deben ser fatales ni reiniciar el temporizador de auto pausa.
- Settings carga los modelos IA desde `/v1/models` usando el backend configurado:
  - muestra la allowlist real del backend;
  - permite seleccionar un modelo;
  - usa fallback local si no se puede cargar la lista;
  - corrige automaticamente el modelo guardado si ya no esta permitido.
- FeedbackScreen permite buscar dentro del historial local y filtrar resultados sin backend.
- FirstLaunchSetupScreen pulido:
  - tema oscuro alineado con la app;
  - seleccion visual de asistente con genero;
  - setup rapido mantiene nombre, asistente y Continue.
- SettingsScreen redisenado:
  - dos voces femeninas y dos masculinas;
  - voces diferenciadas por edad percibida: joven y mayor para femenino, joven y mayor para masculino;
  - modelo IA por combo box;
  - silence timeout y auto pausa por barra deslizante hasta 5 minutos;
  - comandos de voz con combo box de accion y caja de texto para la frase;
  - backend en seccion desplegable;
  - boton Save fijo mientras se hace scroll y con color propio para distinguirse;
  - boton Back fijo con tono rojo y tamano proporcional al boton Save;
  - si hay cambios sin guardar al salir, pregunta si se quieren guardar o descartar.
- HomeScreen bloquea inmediatamente el boton PLAY al tocarlo para evitar doble inicio de conversacion.
- Backend desplegado en Cloudflare Workers:
  - URL publica: `https://english-car-backend.jaccs3000.workers.dev`;
  - `/health` validado;
  - `/v1/models` validado con `APP_API_TOKEN`;
  - secrets remotos `OPENAI_API_KEY` y `APP_API_TOKEN` configurados.

Pendiente de validar en dispositivo/carro:

- App Android usando backend remoto con datos moviles, sin `adb reverse`.
- Android Auto con DHU o vehiculo real.
- Reconexion Bluetooth/Android Auto.
- Comportamiento durante llamada real o audio externo.
- Pulido visual final de HomeScreen/ConversationScreen.

---

## 34. Reglas absolutas para desarrollo

Codex no debe:

- Agregar funcionalidades no definidas.
- Crear arquitectura compleja.
- Sobreingenierizar.
- Agregar librerias innecesarias.
- Crear pantallas adicionales.
- Crear dashboards.
- Crear analytics.
- Crear funcionalidades sociales.
- Cambiar stack sin pedir confirmacion.
- Crear capas innecesarias.
- Crear multiples managers redundantes.
- Crear sistemas enterprise.

Si algo no esta definido, debe considerarse fuera del alcance hasta que se aclare.

---

## 35. Resultado final esperado

El producto final debe sentirse:

- Conversacional.
- Premium.
- Fluido.
- Moderno.
- Rapido.
- Inteligente.
- Natural.
- Comodo para conducir.
- Poco invasivo.
- Humano.

Objetivo final:

Crear un copiloto conversacional de American English moderno, rapido y muy bien ejecutado para Android Auto, con backend propio para seguridad y streaming de IA.
