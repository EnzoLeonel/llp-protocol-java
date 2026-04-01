# LLP Protocol - Implementación en Java

[![Maven Package](https://github.com/EnzoLeonel/llp-protocol-java/actions/workflows/maven-publish.yml/badge.svg)](https://github.com/EnzoLeonel/llp-protocol-java/actions/workflows/maven-publish.yml)
[![Licencia: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-blue)](https://www.oracle.com/java/)
[![codecov](https://codecov.io/github/EnzoLeonel/llp-protocol/graph/badge.svg?token=A6Q68GQDBJ)](https://codecov.io/github/EnzoLeonel/llp-protocol)

Implementación en **Java 21** del protocolo **LLP (Lightweight Link Protocol)** para comunicación robusta, eficiente y extensible entre microcontroladores y aplicaciones Java.

---

## 🚀 Características

* ✅ **Liviano:** Optimizado para microcontroladores y entornos con recursos limitados
* 🛡️ **Robusto:** CRC16-CCITT, sincronización tolerante a ruido, timeouts
* 🔧 **Extensible:** Hasta 256 tipos de mensaje personalizables
* ⚡ **Bidireccional:** Soporta request-response y eventos asíncronos
* 📦 **Agnóstico al transporte:** Funciona sobre UART, RF, RS485, TCP/IP, etc.
* 🧵 **Preparado para concurrencia:** Diseñado para procesamiento en un solo hilo con manejo de eventos
* 📚 **Documentado:** Javadoc, ejemplos y tests incluidos

---

## 📋 Requisitos

* **Java:** 21 o superior
* **Maven:** 3.6 o superior

---

## 📦 Instalación

### Usando Maven (GitHub Packages)

Agregá la dependencia en tu `pom.xml`:

```xml
<dependency>
    <groupId>com.flamingo</groupId>
    <artifactId>llp-protocol</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

### ⚠️ Requisito: Configurar acceso a GitHub Packages

Esta librería está publicada en GitHub Packages, por lo que es necesario autenticarse.

#### 1. Crear un Personal Access Token

En GitHub:

* Ir a: Settings → Developer Settings → Personal Access Tokens
* Crear un token con permisos:

    * `read:packages`

---

#### 2. Configurar `settings.xml`

Editar o crear:

```id="settings"
~/.m2/settings.xml
```

```xml
<servers>
    <server>
        <id>github</id>
        <username>TU_USUARIO</username>
        <password>TU_TOKEN</password>
    </server>
</servers>
```

---

#### 3. Agregar repositorio en `pom.xml`

```xml
<repositories>
    <repository>
        <id>github</id>
        <name>GitHub Packages</name>
        <url>https://maven.pkg.github.com/EnzoLeonel/llp-protocol-java</url>
    </repository>
</repositories>
```

---

### ✅ Verificación

Luego de configurar todo:

```bash
mvn clean install
```

---

## 🏃 Inicio Rápido

```java
import com.flamingo.comm.llp.*;

LLPParser parser = LLP.newParser();

// Listener de eventos
parser.addListener(new LLPParser.LLPFrameListener() {
    @Override
    public void onFrameReceived(LLPFrame frame) {
        System.out.println("Frame recibido: " + frame);
    }

    @Override
    public void onFrameError(byte errorCode) {
        System.out.println("Error: 0x" + Integer.toHexString(errorCode));
    }
});

// Simulación de lectura desde un stream (UART, TCP, etc.)
InputStream in = ...;

int data;
while ((data = in.read()) != -1) {
    try {
        LLPFrame frame = parser.processByte((byte) data);
        if (frame != null) {
            // Procesar frame completo
        }
    } catch (LLPException e) {
        System.err.println("Error: " + e.getMessage());
    }
}

// Enviar un frame
byte[] payload = "Hello".getBytes();
byte[] frame = LLP.buildData(1, payload);
outputStream.write(frame);
```

---

## 📦 Estructura del Frame

| Campo   | Tamaño  | Descripción             |
| ------- | ------- | ----------------------- |
| Magic   | 2 bytes | 0xAA 0x55               |
| Type    | 1 byte  | Tipo de mensaje         |
| ID      | 2 bytes | ID de transacción (LE)  |
| Length  | 2 bytes | Tamaño del payload (LE) |
| Payload | N bytes | Datos                   |
| CRC16   | 2 bytes | CRC-CCITT (LE)          |

---

## 🔌 Tipos de Mensaje

| Tipo      | Valor | Descripción            |
| --------- | ----- | ---------------------- |
| `PING`    | 0x01  | Prueba de enlace       |
| `ACK`     | 0x02  | Confirmación positiva  |
| `NACK`    | 0x03  | Confirmación negativa  |
| `DATA`    | 0x10  | Datos genéricos        |
| `CONFIG`  | 0x11  | Configuración          |
| `STATUS`  | 0x12  | Estado del dispositivo |
| `COMMAND` | 0x13  | Comando a ejecutar     |
| `EVENT`   | 0x14  | Evento                 |
| `ERROR`   | 0x15  | Error                  |

👉 Tipos personalizados: `0x16` a `0xFF`

---

## 🛠️ Arquitectura

```
com.flamingo.comm
  └── llp
      ├── LLP.java                 # Facade principal
      ├── LLPParser.java           # Parser (state machine)
      ├── LLPFrame.java            # Modelo de datos
      ├── LLPFrameBuilder.java     # Builder de frames
      ├── LLPMessageType.java      # Enum tipos
      ├── LLPErrorCode.java        # Enum errores
      ├── crc/
      │   └── CRC16CCITT.java      # CRC
      └── util/
          └── Statistics.java      # Métricas
```

---

## ⚠️ Alcance del Protocolo

LLP es un protocolo de transporte liviano. **NO incluye:**

* Identificación de dispositivos
* Routing
* Manejo de sesiones
* Encriptación

👉 Estas funcionalidades deben implementarse en capas superiores si son necesarias.

---

## 🔌 Ejemplo con TCP

```java
Socket socket = new Socket("192.168.1.10", 23);

InputStream in = socket.getInputStream();
OutputStream out = socket.getOutputStream();

LLPParser parser = LLP.newParser();

// Hilo de recepción
new Thread(() -> {
    try {
        int b;
        while ((b = in.read()) != -1) {
            LLPFrame frame = parser.processByte((byte) b);
            if (frame != null) {
                System.out.println("RX: " + frame);
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
}).start();

// Enviar PING
byte[] frame = LLP.buildPing(1);
out.write(frame);
```

---

## 🧪 Tests

```bash
mvn test
mvn verify
```

## 📊 Rendimiento

* Procesamiento eficiente byte a byte
* Bajo overhead de memoria
* Implementación de CRC optimizada

---

## 🤝 Contribuciones

Las contribuciones son bienvenidas:

1. Fork del repositorio
2. Crear rama (`feature/nueva-funcionalidad`)
3. Commit
4. Push
5. Pull Request

---

## 📜 Licencia

MIT License - ver [LICENSE](LICENSE)

---

## ✍️ Autor

Creado por **EnzoLeonel**

---

**Versión:** 1.0.0
**Última actualización:** 2026-03-31
**Java Target:** 21+
