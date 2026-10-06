/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package bai07_bt04;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/**
 * RC_Protocol.java
 * Dinh nghia giao thuc dung chung giua Host (may bi dieu khien) va Controller (may dieu khien).
 *
 * - Hinh anh man hinh (video) duoc truyen qua TCP: can do toan ven tuyet doi, 1 byte sai
 *   la hong ca khung hinh, nen khong the dung UDP cho phan nay.
 * - Lenh dieu khien chuot/ban phim duoc truyen qua UDP: can TOC DO CAO, chap nhan mat goi,
 *   chi quan tam goi MOI NHAT, dung dung tinh chat "best-effort, khong dam bao thu tu" cua UDP.
 */
public class RC_Protocol {
    public static final int TCP_SCREEN_PORT = 7000;  // cong truyen hinh anh man hinh
    public static final int UDP_CONTROL_PORT = 7001; // cong nhan lenh dieu khien

    // Cac loai goi tin dieu khien gui qua UDP
    public static final byte TYPE_MOVE       = 1; // di chuyen chuot toi toa do (x, y)
    public static final byte TYPE_MOUSE_DOWN = 2; // nhan nut chuot
    public static final byte TYPE_MOUSE_UP   = 3; // tha nut chuot
    public static final byte TYPE_WHEEL      = 4; // cuon chuot
    public static final byte TYPE_KEY_DOWN   = 5; // nhan phim
    public static final byte TYPE_KEY_UP     = 6; // tha phim

    // Ma nut chuot dung rieng cho giao thuc nay (doc lap voi InputEvent cua he dieu hanh)
    public static final int BUTTON_LEFT   = 1;
    public static final int BUTTON_MIDDLE = 2;
    public static final int BUTTON_RIGHT  = 3;

    // Kich thuoc co dinh cua 1 goi tin dieu khien: 8 byte seq + 1 byte type + 4 byte p1 + 4 byte p2 + 4 byte p3
    public static final int PACKET_SIZE = 8 + 1 + 4 + 4 + 4;

    /**
     * Dong goi 1 lenh dieu khien thanh mang byte de gui qua UDP (ho tro ca p3).
     * @param seq so thu tu TANG DAN, ben nhan dua vao day de biet goi nao moi hon
     * @param type loai lenh (xem cac hang so TYPE_...)
     * @param p1 tham so 1 (vi du: toa do x, ma phim, ma nut chuot, do cuon)
     * @param p2 tham so 2 (vi du: toa do y; toa do x cho mouse down/up; khong dung thi truyen 0)
     * @param p3 tham so 3 (vi du: toa do y cho mouse down/up; khong dung thi truyen 0)
     */
    public static byte[] encode(long seq, byte type, int p1, int p2, int p3) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(PACKET_SIZE);
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeLong(seq);
            dos.writeByte(type);
            dos.writeInt(p1);
            dos.writeInt(p2);
            dos.writeInt(p3);
        } catch (IOException ignored) {
            // ByteArrayOutputStream khong bao gio nem IOException thuc su
        }
        return baos.toByteArray();
    }

    public static byte[] encode(long seq, byte type, int p1, int p2) {
        return encode(seq, type, p1, p2, 0);
    }

    /** Cau truc luu 1 lenh dieu khien da duoc giai ma tu goi UDP nhan duoc. */
    public static class ControlPacket {
        public long seq;
        public byte type;
        public int p1;
        public int p2;
        public int p3;
    }

    public static ControlPacket decode(byte[] data, int length) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data, 0, length));
        ControlPacket p = new ControlPacket();
        p.seq = dis.readLong();
        p.type = dis.readByte();
        p.p1 = dis.readInt();
        p.p2 = dis.readInt();
        if (dis.available() >= 4) {
            p.p3 = dis.readInt();
        } else {
            p.p3 = 0;
        }
        return p;
    }
}
