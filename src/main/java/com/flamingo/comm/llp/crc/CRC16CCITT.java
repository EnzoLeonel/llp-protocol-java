package com.flamingo.comm.llp.crc;

/**
 * Implementation of CRC16-CCITT.
 * <p>
 * Polynomial: 0x1021
 * Initial value: 0xFFFF
 * <p>
 * Optimized calculation using a pre-calculated table (optional).
 */
public class CRC16CCITT {
    private static final int POLYNOMIAL = 0x1021;
    private static final int INITIAL_VALUE = 0xFFFF;
    private static final int[] CRC_TABLE = buildTable();

    /**
     * Build a precomputed CRC16 table for better performance
     */
    private static int[] buildTable() {
        int[] table = new int[256];
        for (int i = 0; i < 256; i++) {
            int crc = i << 8;
            for (int j = 0; j < 8; j++) {
                crc = (crc & 0x8000) != 0
                        ? ((crc << 1) ^ POLYNOMIAL) & 0xFFFF
                        : (crc << 1) & 0xFFFF;
            }
            table[i] = crc;
        }
        return table;
    }

    /**
     * Calculate the CRC16 of an entire buffer
     */
    public static int calculate(byte[] data) {
        return calculate(data, 0, data.length);
    }

    /**
     * Calculate the CRC16 of a range of bytes
     */
    public static int calculate(byte[] data, int offset, int length) {
        int crc = INITIAL_VALUE;
        for (int i = 0; i < length; i++) {
            crc = updateCRC(crc, data[offset + i]);
        }
        return crc;
    }

    /**
     * Update CRC16 with an additional byte (for incremental calculation)
     */
    public static int updateCRC(int crc, byte data) {
        int index = ((crc >> 8) ^ (data & 0xFF)) & 0xFF;
        return ((crc << 8) ^ CRC_TABLE[index]) & 0xFFFF;
    }

    /**
     * Check whether the data's CRC matches the expected value
     */
    public static boolean verify(byte[] data, int expectedCRC) {
        return calculate(data) == expectedCRC;
    }
}