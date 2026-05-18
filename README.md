# Protocolo LLP — Implementación en Java

[![Maven Package](https://github.com/flamicomm/llp-protocol-java/actions/workflows/maven-publish.yml/badge.svg)](https://github.com/flamicomm/llp-protocol-java/actions/workflows/maven-publish.yml)
[![Licencia: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-blue)](https://www.oracle.com/java/)
[![codecov](https://codecov.io/github/flamicomm/llp-protocol-java/graph/badge.svg?token=BNZ9MLIS2Z)](https://codecov.io/github/flamicomm/llp-protocol-java)

Implementación en **Java 21** de **LLP (Layered Link Protocol)** — un protocolo de comunicación robusto, eficiente y extensible diseñado para la comunicación de dispositivos IoT. LLP está construido en torno a un modelo de cebolla en capas (layered onion model), donde cada trama puede transportar capas de protocolo opcionales sobre la capa de transporte obligatoria.

---

## 🚀 Características

* ✅ **Ligero:** Optimizado para entornos limitados y comunicación con microcontroladores.
* 🧅 **Arquitectura en capas:** Las tramas transportan capas anidadas opcionales (enrutamiento, encriptación, compresión, etc.).
* 🔌 **Sistema de plugins:** Las nuevas capas se añaden como bibliotecas independientes descubiertas automáticamente vía Java SPI.
* 🛡️ **Transporte robusto:** Validación CRC16-CCITT, sincronización tolerante al ruido, *byte stuffing* (relleno de bytes), tiempos de espera configurables.
* 📡 **Agnóstico al transporte:** Funciona sobre UART, RF, RS485, TCP/IP, Bluetooth y cualquier flujo de bytes.
* ⚡ **Preparado para Streaming:** Análisis incremental byte por byte para transportes basados en interrupciones o streaming.
* 🧱 **Separación de responsabilidades:** Tuberías independientes de construcción y análisis de tramas — usa solo lo que necesites.
* 📚 **Completamente documentado:** Javadoc, ejemplos y suite completa de pruebas incluidas.

---

## 📋 Requisitos

* **Java:** 21 o superior
* **Maven:** 3.6 o superior

---

## 📦 Instalación

Añade la dependencia a tu `pom.xml`:

```xml
<dependency>
    <groupId>com.flamingo</groupId>
    <artifactId>llp-core</artifactId>
    <version>3.1.0</version>
</dependency>
```

### Autenticación en GitHub Packages

Esta librería está publicada en GitHub Packages. Se requiere autenticación.

**1. Crea un Token de Acceso Personal (Personal Access Token)**

Ve a: GitHub → Settings → Developer Settings → Personal Access Tokens

Crea un token con el permiso `read:packages`.

**2. Configura `~/.m2/settings.xml`**

```xml
<servers>
    <server>
        <id>github</id>
        <username>TU_USUARIO</username>
        <password>TU_TOKEN</password>
    </server>
</servers>
```

**3. Añade el repositorio a tu `pom.xml`**

```xml
<repositories>
    <repository>
        <id>github</id>
        <name>GitHub Packages</name>
        <url>https://maven.pkg.github.com/flamicomm/llp-protocol-java</url>
    </repository>
</repositories>
```

**4. Verifica**

```bash
mvn clean install
```

---

## 🏃 Inicio Rápido (Quick Start)

### Construcción de una trama (salida / outbound)

```java
import com.flamingo.comm.llp.core.LLP;
import com.flamingo.comm.llp.core.LLPFrameBuilder;

import java.nio.ByteBuffer;

// Minimal frame — transport layer only, no additional layers
LLPFrameBuilder<byte[]> builder = LLP.frameBuilder().build();

byte[] frame = builder.build(ByteBuffer.wrap("hello device".getBytes()));
// uart.write(frame); // Example: send via your preferred transport
```

### Análisis incremental de un flujo de datos (entrada / inbound)

```java
import com.flamingo.comm.llp.core.LLP;
import com.flamingo.comm.llp.core.LLPFrame;
import com.flamingo.comm.llp.core.LLPIncrementalParser;
import com.flamingo.comm.llp.core.FinalNode;
import com.flamingo.comm.llp.core.FailureNode;
import com.flamingo.comm.llp.core.UnknownNode;
import com.flamingo.comm.llp.core.TransportErrorCode;

LLPIncrementalParser parser = LLP.incrementalParser()
        .maxPayloadBytes(4096)
        .timeoutMs(2000)
        .build();

// Feed bytes as they arrive from the transport (UART, TCP, etc.)
// InputStream in = serialPort.getInputStream(); // Example
int b;
while ((b = in.read()) != -1) {
    parser.feed((byte) b);

    for (LLPFrame frame : parser.pollFrames()) {
        // Navigate the node chain using the visitor pattern
        frame.chain().visit(visitor -> visitor
            .on(FinalNode.class, node -> {
                byte[] payload = new byte[node.getPayload().remaining()];
                node.getPayload().get(payload);
                System.out.println("Received: " + new String(payload));
            })
            .on(UnknownNode.class, node ->
                System.out.println("Unknown layer skipped: ID=" + node.getId()))
            .on(FailureNode.class, node ->
                System.err.println("Layer failed: ID=" + node.getId()
                    + " reason=" + node.getErrorReason()))
        );
    }

    for (TransportErrorCode error : parser.pollErrors()) {
        System.err.println("Transport error: " + error);
    }
}
```

### Análisis de tramas completas (no streaming)

```java
LLPFrameParser parser = LLP.frameParser().build();

// rawFrame is an LLPRawFrame produced by LLPTransportDeframer
LLPFrame frame = parser.parse(rawFrame);
```

### Uso de plugins de capas

Cuando una librería de capa (ej. `llp-layer-routing`) está presente en el classpath, es descubierta automáticamente vía SPI — no se requiere configuración.

```java
// Frame building with layers
LLPFrameBuilder<byte[]> builder = LLP.frameBuilder()
        .addLayer(new RoutingLayerBuilder("sensor-42", "zone-north"))   // inner layer
        .addLayer(new EncryptionLayerBuilder(Algorithm.AES_256_GCM, key)) // outer layer
        .build();

byte[] frame = builder.build(ByteBuffer.wrap(telemetryData));

// Parsing with layers (handlers discovered automatically via SPI)
LLPIncrementalParser parser = LLP.incrementalParser().build();

parser.feed(frame);
LLPFrame parsed = parser.pollFrames().getFirst();

// Access metadata from a specific layer
parsed.chain().getNode(RoutingNode.class).ifPresent(node -> {
    System.out.println("Device: " + node.getMetadata().deviceId());
    System.out.println("Group:  " + node.getMetadata().group());
});
```

---

## 📦 Estructura de la Trama

### Trama de transporte

El envoltorio más externo validado por la capa de transporte:

```
[MAGIC 2B][LENGTH 2B][PAYLOAD NB][CRC16 2B]
```

| Campo | Tamaño | Valor / Descripción |
| --- | --- | --- |
| Magic | 2 bytes | `0xAA 0x55` — delimitador de trama |
| Length | 2 bytes | Tamaño del payload en bytes (little-endian) |
| Payload | N bytes | Cadena de capas codificada (ver abajo) |
| CRC16 | 2 bytes | CRC16-CCITT sobre Magic + Length + Payload (LE) |

Se aplica *byte stuffing* a todos los campos excepto Magic: cualquier byte `0xAA` en el flujo se escapa como `0xAA 0x00`. Una secuencia inesperada `0xAA 0x55` dentro de una trama señala un evento de resincronización.

### Payload de capa (dentro de la trama de transporte)

El payload contiene una cadena recursiva de capas opcionales seguida de los datos crudos finales:

```
[LAYER_ID][META_LENGTH][METADATA ...][  next layer or final  ]
                                      ↓
                              [0x00][RAW PAYLOAD BYTES]
```

| Campo | Tamaño | Descripción |
| --- | --- | --- |
| Layer ID | 1 byte | Identifica la capa. `0` = payload final. Ver reglas abajo. |
| Meta length | 1–3 bytes | Tamaño de los metadatos. Valores `0–254` usan 1 byte; `≥255` usan `0xFF` + 2 bytes (big-endian) |
| Metadata | N bytes | Metadatos específicos de la capa (definidos por cada librería de capa) |
| Payload | Resto | Siguiente capa o bytes crudos finales |

#### Reglas de Layer ID (ID de Capa)

| Rango de ID | Significado |
| --- | --- |
| `0` | **Final node (Nodo final)** — no hay más capas; los bytes restantes son el payload crudo de la aplicación. |
| `1–127` | **Passthrough layer (Capa de paso)** — los metadatos pueden omitirse; el contenido del payload no cambia. |
| `128–254` | **Transform layer (Capa de transformación)** — el payload fue modificado (encriptado, comprimido, etc.); no puede omitirse sin la librería de la capa. |
| `255` | **Reservado** — reservado para uso futuro; los parsers deben tratarlo como desconocido y saltarlo si es posible. |

---

## 🏛️ Arquitectura

```
com.flamingo.comm.llp/
│
├── core/                          # Core library — transport + layer parsing
│   ├── LLP.java                   # Static entry point and factory
│   ├── LLPFrameBuilder.java       # Outbound frame builder interface
│   ├── LLPFrameParser.java        # Inbound frame parser interface
│   ├── LLPIncrementalParser.java  # Streaming/incremental inbound parser
│   ├── LLPTransportFramer.java    # Transport framing (magic, CRC, stuffing)
│   ├── LLPTransportDeframer.java  # Transport deframing state machine
│   ├── LLPFrame.java              # Parsed frame with node chain
│   ├── LLPRawFrame.java           # Transport-level validated frame
│   ├── NodeChain.java             # Immutable ordered chain of nodes
│   ├── NodeVisitor.java           # Type-safe visitor for node traversal
│   ├── FinalNode.java             # Terminal node (raw payload)
│   ├── UnknownNode.java           # Skipped unknown passthrough layer
│   ├── FailureNode.java           # Failed-to-parse layer node
│   ├── ByteArrayFrameBuilder.java # Default frame builder (byte[] output)
│   ├── CoreParseErrorReason.java  # Core-level parse error reasons
│   ├── TransportErrorCode.java    # Transport-level error codes
│   ├── FrameBuildException.java   # Exception for frame build failures
│   ├── LayerParserProvider.java    # Functional interface for parser lookup
│   ├── LayerParserRegistry.java   # SPI-based layer parser registry
│   └── SimpleFrameParser.java     # Internal default frame parser
│
├── spi/                           # SPI contracts for layer plugins
│   ├── LLPLayerParser.java        # Interface for inbound layer parsing
│   ├── LLPLayerBuilder.java       # Interface for outbound layer building
│   ├── LLPNode.java               # Base node interface
│   ├── LayerParseResult.java      # Sealed result type (Success | Failure)
│   ├── LayerBuildResult.java      # Sealed result type (Success.UnmodifiedPayload | Success.TransformedPayload | Failure)
│   ├── LayerParseInput.java       # Read-only metadata + payload for parsing
│   ├── LayerBuildPayload.java      # Read-only payload for building
│   ├── ParseErrorReason.java      # Interface for parse error reasons
│   └── BuildErrorReason.java      # Interface for build error reasons
│
└── util/                          # Internal utilities
    ├── ByteWriter.java            # Utility for writing byte sequences
    ├── CRC16CCITT.java            # CRC16-CCITT calculation
    ├── LayerIds.java              # Layer ID rules and classification
    └── Statistics.java            # Transport statistics tracking
```

### Puntos de entrada

`LLP` expone tres métodos de fábrica independientes — usa solo lo que tu caso de uso requiera:

```java
// Outbound only — build and serialize frames
LLPFrameBuilder<byte[]> builder = LLP.frameBuilder()
        .addLayer(...)
        .build();

// Inbound only — parse complete LLPRawFrame objects
LLPFrameParser parser = LLP.frameParser()
        .parserProvider(customProvider) // optional; defaults to SPI discovery
        .build();

// Inbound only — streaming, byte-by-byte, pull-based
LLPIncrementalParser incremental = LLP.incrementalParser()
        .maxPayloadBytes(8192)
        .timeoutMs(1000)
        .build();
```

### Tubería de entrada (Inbound pipeline)

```
byte stream
    └── LLPTransportDeframer   (sync · unstuffing · CRC validation)
          └── LLPRawFrame
                └── SimpleFrameParser   (layer chain parsing via SPI registry)
                      └── LLPFrame  →  NodeChain  →  [Node, Node, ..., FinalNode]
```

### Tubería de salida (Outbound pipeline)

```
ByteBuffer payload
    └── ByteArrayFrameBuilder   (applies layer builders in order)
          └── byte[] (layered payload)
                └── LLPTransportFramer   (magic · length · stuffing · CRC)
                      └── byte[] (transport frame ready to transmit)
```

---

## 🔌 Sistema de Plugins (SPI)

Las nuevas capas de protocolo se implementan como módulos de Maven independientes. La librería base las descubre en tiempo de ejecución utilizando el `ServiceLoader` de Java — no se requiere registro manual.

### Creación de un plugin de capa

**1. Añade la dependencia base (core)**

```xml
<dependency>
    <groupId>com.flamingo</groupId>
    <artifactId>llp-core</artifactId>
    <version>3.1.0</version>
</dependency>
```

**2. Implementa `LLPLayerParser` (entrada)**

```java
public class RoutingLayerParser implements LLPLayerParser {

    public static final int LAYER_ID = 45; // 1–127: passthrough

    @Override
    public int getLayerId() { return LAYER_ID; }

    @Override
    public LayerParseResult parse(LayerParseInput input) {
        MetadataReader reader = MetadataReader.wrap(input.metadata());
        try {
            String deviceId = reader.readUtf8(reader.readUInt8());
            String group    = reader.readUtf8(reader.readUInt8());
            int    ttl      = reader.readUInt8();

            return new LayerParseResult.Success(
                new RoutingNode(new RoutingMetadata(deviceId, group, ttl)),
                input.payload()  // passthrough: payload unchanged
            );
        } catch (Exception e) {
            return new LayerParseResult.Failure(
                MyErrorReason.INVALID_METADATA
            );
        }
    }
}
```

> **Nota:** `LayerParseResult.Success` y `LayerParseResult.Failure` son records internos de la sealed interface `LayerParseResult`. Usa sus constructores directamente. Para errores, puedes implementar `ParseErrorReason` con tus propios enums, o usar `CoreParseErrorReason` del paquete `core`.

**3. Implementa `LLPLayerBuilder` (salida)**

```java
public class RoutingLayerBuilder implements LLPLayerBuilder {

    private final String deviceId;
    private final String group;

    public RoutingLayerBuilder(String deviceId, String group) {
        this.deviceId = deviceId;
        this.group    = group;
    }

    @Override
    public int getLayerId() { return RoutingLayerParser.LAYER_ID; }

    @Override
    public LayerBuildResult build(LayerBuildPayload payload) {
        byte[] deviceIdBytes = deviceId.getBytes(StandardCharsets.UTF_8);
        byte[] groupBytes    = group.getBytes(StandardCharsets.UTF_8);

        byte[] metadata = MetadataWriter.create()
                .writeUInt8(deviceIdBytes.length).writeBytes(deviceIdBytes)
                .writeUInt8(groupBytes.length).writeBytes(groupBytes)
                .writeUInt8(3)  // TTL default
                .toBytes();

        // Passthrough: payload is not modified
        return new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.wrap(metadata));
    }
}
```

**4. Registro vía SPI**

Crea el archivo `src/main/resources/META-INF/services/com.flamingo.comm.llp.spi.LLPLayerParser`:

```
com.example.llp.routing.RoutingLayerParser
```

La librería base lo descubrirá y registrará automáticamente al inicio.

### Asignación de Layer ID

| Rango | Tipo | Comportamiento del Payload | Requiere SPI para decodificar |
| --- | --- | --- | --- |
| `1–127` | Passthrough | Sin cambios — puede omitirse | No |
| `128–254` | Transform | Modificado (encriptado/comprimido) | Sí |
| `255` | Reservado | Tratado como desconocido y saltado | No |

---

## 🧩 Tipos de Nodos

Tras el análisis, el `NodeChain` de la trama contiene una secuencia ordenada de nodos desde el más externo al más interno:

| Tipo de nodo | Cuándo se crea | Métodos clave |
| --- | --- | --- |
| `LLPNode` | Implementaciones SPI (capas personalizadas) | `getId()` |
| `FinalNode` | Siempre — marca el final de la cadena con bytes crudos | `getId()`, `getPayload()` |
| `UnknownNode` | El Layer ID no tiene manejador y es de tipo passthrough o reservado | `getId()`, `getMetadata()` |
| `FailureNode` | Falló el análisis de la capa (error de plugin, metadatos corruptos o falta de FinalNode) | `getId()`, `getErrorReason()`, `getCause()`, `getMetadata()` |

### Navegación por la cadena

```java
// Option A — visitor pattern (recommended for production code)
frame.chain().visit(visitor -> visitor
    .on(FinalNode.class, node -> handlePayload(node.getPayload()))
    .on(UnknownNode.class, node ->
        System.out.println("Skipped layer " + node.getId()))
    .on(FailureNode.class, node ->
        System.err.println("Failed layer " + node.getId()
            + ": " + node.getErrorReason()))
);

// Option B — find a specific node type
frame.chain().getNode(RoutingNode.class)
     .ifPresent(n -> route(n.getMetadata().deviceId()));

// Option C — find a node by layer ID
frame.chain().getNode(45)
     .ifPresent(n -> System.out.println("Routing node: " + n));

// Option D — access the raw payload directly (innermost node)
LLPNode deepest = frame.chain().getDeepestNode();
if (deepest instanceof FinalNode final) {
    process(final.getPayload());
}
```

---

## 🔄 Migración desde v2.x

La versión 3 introduce un nuevo modelo de tramas en capas y una API pública rediseñada. La API de la v2 ha sido eliminada.

| v2.x | v3.x |
| --- | --- |
| `LLP.newParser()` | `LLP.incrementalParser().build()` |
| `parser.processByte(b)` | `parser.feed(b)` + `parser.pollFrames()` |
| `parser.addListener(...)` | Maneja los resultados desde `pollFrames()` / `pollErrors()` |
| `LLP.buildData(type, payload)` | `LLP.frameBuilder().build()` + `.build(payload)` |
| `LLPFrame.getType()` | Eliminado — el tipo de mensaje es ahora un asunto de la capa |
| `LLPFrame.getId()` | Eliminado — el ID de transacción es ahora un asunto de la capa |
| `LLPMessageType` | Eliminado — define los tipos de mensajes en tu capa |
| Formato de trama única | Modelo de cebolla en capas (layered onion model) con capas opcionales |

El formato de la trama cambió significativamente en la v3 para soportar el modelo de capas. Las tramas v2 y v3 **no son compatibles a nivel de red (wire-compatible)**.

---

## 🧪 Pruebas

```bash
# Run unit and integration tests
mvn test

# Run tests with coverage report
mvn verify

# Run tests including slow timing vectors
mvn test -Pslow-tests
```

La suite de pruebas cubre:

* *Framing* y *deframing* de transporte (incluyendo byte stuffing, CRC, timeouts, recuperación de sincronización).
* Análisis de cadena de capas (capas conocidas, desconocidas y con fallos).
* Construcción de tramas con una o múltiples capas.
* Análisis incremental (streaming) a través de los tres métodos `feed()`.
* Registro SPI (detección de duplicados, registro manual, descubrimiento SPI).
* Casos límite (edge cases): payloads vacíos, longitudes de metadatos extendidas, fallos no omitibles (non-skippable).
* Vectores de prueba de especificación LLP: 199 vectores oficiales que validan conformancia wire-compatible.

---

## 📊 Notas de rendimiento

* Cero copias intermedias durante la creación de tramas de transporte (`LLPTransportFramer`).
* Una única asignación de memoria para el array final de la trama en `ByteArrayFrameBuilder`.
* Uso intensivo de `ByteBuffer.slice()` y `duplicate()` para evitar copias de datos durante el análisis (parsing).
* `NodeChain.Builder` perezoso (lazy) — sin asignación de memoria hasta que se añade el primer nodo.
* Resultados inmutables — todos los objetos analizados son seguros para compartir entre hilos (threads) después de su creación.

---

## 🤝 Contribuir

Las contribuciones son bienvenidas:

1. Haz un Fork del repositorio.
2. Crea una rama (`feature/nueva-caracteristica`).
3. Haz un Commit con tus cambios.
4. Haz Push y abre un Pull Request.

Todo el código, comentarios, Javadoc y nombres de variables deben escribirse en **Inglés**.

---

## 📜 Licencia

Licencia MIT — ver [LICENSE](LICENSE)

LLP Specification v3.1.0 — Copyright © 2026 Flamingo Communications

This specification is maintained as the authoritative reference for the LLP protocol. All implementations should reference this document as the canonical behaviour definition.

---

## ✍️ Autor

Creado por **Famingo Communications**

---

**Versión:** 3.1.0

**Última actualización:** 2026-05-17

**Objetivo Java:** 21+