package nl.bytesoflife;

public class CRC8
{
    private static final int CRC_PRESET = 0xFF;
    private static final int CRC_POLYNOM = 0x9c;

    /** Corresponding C code:

     #define CRC_POLYNOM 0x9c
     #define CRC_PRESET 0xFF

     unsigned char getCRC8(char* data, int len)
     {
     int i,j;
     unsigned int crc = CRC_PRESET;

     for (i = 0; i < len; i++)
     {
     crc ^= data[i] & 0xFF;
     for (j = 0; j < 8; j++)
     {
     if ((crc & 0x01) == 0)
     {
     crc = (crc >> 1) ^ CRC_POLYNOM;
     }
     else
     {
     crc = (crc >> 1);
     }
     }
     }

     return (crc & 0xFF);
     }

     **/

    public static int calculateCRC8(byte[] b)
    {
        int crc = CRC_PRESET;
        int len= b.length;

        for (int i = 0; i < len; i++)
        {
            crc ^= b[i] & 0xff;
            for (int j = 0; j < 8; j++)
            {
                if ((crc & 0x01) == 0)
                {
                    crc = (crc >> 1) ^ CRC_POLYNOM;
                }
                else
                {
                    crc = crc >> 1;
                }
            }
        }
        return crc & 0xFF;
    }

    public static void  main(String[] args)
    {
        final int   CRC_POLYNOM = 0x9C;
        final byte  CRC_INITIAL = (byte)0xFF;

        final byte[]    data = {1, 56, -23, 3, 0, 19, -125, 0, 2, 0, 3, 13, 8, -34, 7, 9, 42, 18, 26, -5, 54, 11, -94, -46, -128, 4, 48, 52, 0, 0, 0, 0, 0, 0, 0, 0, 4, 1, 1, -32, -80, 0, 98, -5, 71, 0, 64, 0, 0, 0, 0, -116, 1, 104, 2};

        long res= calculateCRC8(data);

        System.out.println("res= " + res);
    }
}
