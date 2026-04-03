package com.flamingo.llp.test;

import com.fazecast.jSerialComm.SerialPort;
import com.flamingo.comm.llp.LLP;
import com.flamingo.comm.llp.LLPFrame;
import com.flamingo.comm.llp.LLPParser;

import java.io.InputStream;
import java.io.OutputStream;

public class SerialLLPTest {

    public static void main(String[] args) throws Exception {

        // ================= SERIAL CONFIG =================
        SerialPort port = SerialPort.getCommPort("/dev/ttyUSB0");
        port.setBaudRate(9600);
        port.setNumDataBits(8);
        port.setNumStopBits(SerialPort.ONE_STOP_BIT);
        port.setParity(SerialPort.NO_PARITY);
        port.setComPortTimeouts(
                SerialPort.TIMEOUT_READ_BLOCKING,
                0,
                0
        );

        if (!port.openPort()) {
            System.err.println("❌ Cannot open serial port");
            return;
        }

        System.out.println("✅ Connected to " + port.getSystemPortName());
        System.out.println("⏳ Waiting for Arduino reset...");
        Thread.sleep(2000);

        InputStream in = port.getInputStream();
        OutputStream out = port.getOutputStream();

        // ================= LLP =================
        LLPParser parser = LLP.newParser();

        parser.addListener(new LLPParser.LLPFrameListener() {
            @Override
            public void onFrameReceived(LLPFrame frame) {
                System.out.println("[RX] " + frame);

                switch (frame.type()) {
                    case 0x01: // PING
                        System.out.println("→ PING recibido");
                        break;

                    case 0x02: // ACK
                        System.out.println("→ ACK recibido");
                        break;

                    case 0x10: // DATA
                        System.out.println("→ DATA: " + new String(frame.payload()));
                        break;
                }
            }

            @Override
            public void onFrameError(byte errorCode) {
                System.err.println("[ERR] 0x" + Integer.toHexString(errorCode));
            }
        });

        // ================= RX THREAD =================
        Thread reader = new Thread(() -> {
            StringBuilder asciiBuffer = new StringBuilder();

            while (true) {
                try {
                    int data = in.read();

                    if (data >= 0) {
                        byte b = (byte) data;

                        // ===== PRINT POR BLOQUES =====
                        if (b == '\n') {
                            System.out.println("[RAW ASCII] " + asciiBuffer);
                            asciiBuffer.setLength(0);
                        } else {
                            asciiBuffer.append((char) b);
                        }

                        // ===== PARSER =====
                        parser.processByte(b);
                    }

                } catch (Exception e) {
                    System.err.println("[WARN] Read error: " + e.getMessage());
                }
            }
        });

        reader.setDaemon(true);
        reader.start();

        // ================= TX LOOP =================
        int id = 1;

        while (true) {

            // Enviar PING
            byte[] ping = LLP.buildPing(id++);
            out.write(ping);
            out.flush();

            System.out.println("[TX] PING");

            Thread.sleep(3000);

            // Enviar DATA
            byte[] data = LLP.buildData(id++, "Hello from Java".getBytes());
            out.write(data);
            out.flush();

            System.out.println("[TX] DATA");

            Thread.sleep(5000);
        }
    }
}