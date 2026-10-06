/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package bai07_bt04;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * RC_HostServer.java
 * Chay vai tro HOST tren MAY BI DIEU KHIEN (vi du may B khi A dang dieu khien B).
 *
 * Gom 2 nhiem vu chay song song doc lap:
 *  1) TCP Server (cong 7000): chup man hinh lien tuc bang Robot.createScreenCapture(),
 *     nen JPEG, gui tung khung hinh cho Controller dang xem - giong co che cua AnyDesk/TeamViewer.
 *  2) UDP Server (cong 7001): nhan cac lenh dieu khien chuot/ban phim, dung java.awt.Robot
 *     de THUC THI NGAY tren may nay. Loai bo goi tin cu (seq nho hon goi da xu ly cung loai)
 *     de dam bao luon thuc hien lenh MOI NHAT, khong bi do tre khi mang nghen.
 */
public class RC_HostServer {
    private final Robot robot;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread tcpThread;
    private Thread udpThread;
    private final PrintStream console;

    // Luu seq lon nhat da xu ly RIENG CHO TUNG LOAI lenh (MOVE, MOUSE_DOWN, KEY_DOWN, ...),
    // de 1 goi MOVE den cham khong vo tinh lam "nuot mat" 1 lenh MOUSE_DOWN/KEY_DOWN quan trong hon
    private final long[] lastSeqByType = new long[10];

    public RC_HostServer(PrintStream console) throws AWTException {
        this.console = console;
        this.robot = new Robot();
        this.robot.setAutoDelay(0); // khong tre gia lap giua cac thao tac, uu tien phan hoi nhanh
    }

    public void start() {
        if (running.get()) return;
        running.set(true);

        tcpThread = new Thread(this::runScreenServer, "ScreenServerThread");
        tcpThread.setDaemon(true);
        tcpThread.start();

        udpThread = new Thread(this::runControlServer, "ControlServerThread");
        udpThread.setDaemon(true);
        udpThread.start();

        console.println("[Host] Đã bật chế độ cho phép máy khác điều khiển mình.");
        console.println("[Host] Chờ Controller kết nối lấy màn hình tại cổng TCP " + RC_Protocol.TCP_SCREEN_PORT);
        console.println("[Host] Đang lắng nghe lệnh điều khiển tại cổng UDP " + RC_Protocol.UDP_CONTROL_PORT);
    }

    public void stop() {
        running.set(false);
        if (tcpThread != null) tcpThread.interrupt();
        if (udpThread != null) udpThread.interrupt();
        console.println("[Host] Đã tắt chế độ cho phép điều khiển từ xa.");
    }

    // ===================== PHAN 1: TRUYEN HINH ANH MAN HINH QUA TCP =====================
    private void runScreenServer() {
        try (ServerSocket serverSocket = new ServerSocket(RC_Protocol.TCP_SCREEN_PORT)) {
            while (running.get()) {
                Socket socket = serverSocket.accept();
                console.println("[Host] Controller đã kết nối xem màn hình: "
                        + socket.getInetAddress().getHostAddress());
                Thread t = new Thread(() -> streamScreenTo(socket), "StreamClientThread");
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException e) {
            if (running.get()) console.println("[Host] Lỗi TCP screen server: " + e.getMessage());
        }
    }

    private void streamScreenTo(Socket socket) {
        try (Socket s = socket;
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()))) {

            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            // Gui truoc kich thuoc man hinh THAT cua Host, de Controller quy doi toa do chuot
            // tu cua so hien thi sang toa do thuc tren man hinh Host cho chinh xac
            out.writeInt(screenSize.width);
            out.writeInt(screenSize.height);
            out.flush();

            Rectangle captureArea = new Rectangle(screenSize);

            // Cau hinh ImageWriter JPEG voi muc nen cao ro net (0.9) tranh mo van ban/icon
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                throw new IllegalStateException("Không tìm thấy ImageWriter cho định dạng JPEG");
            }
            ImageWriter writer = writers.next();
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(0.9f);

            try {
                while (running.get() && !s.isClosed()) {
                    BufferedImage image = robot.createScreenCapture(captureArea);

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                        writer.setOutput(ios);
                        writer.write(null, new IIOImage(image, null, null), writeParam);
                        ios.flush();
                    }
                    byte[] frameBytes = baos.toByteArray();

                    out.writeInt(frameBytes.length);
                    out.write(frameBytes);
                    out.flush();

                    Thread.sleep(80); // ~12 khung hinh/giay - du muot de dieu khien tu xa, khong qua nang mang
                }
            } finally {
                writer.dispose();
            }
        } catch (Exception e) {
            console.println("[Host] Controller đã ngắt kết nối xem màn hình.");
        }
    }

    // ===================== PHAN 2: NHAN VA THUC THI LENH DIEU KHIEN QUA UDP =====================
    private void runControlServer() {
        try (DatagramSocket udpSocket = new DatagramSocket(RC_Protocol.UDP_CONTROL_PORT)) {
            byte[] buffer = new byte[Math.max(RC_Protocol.PACKET_SIZE, 256)];

            while (running.get()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);

                RC_Protocol.ControlPacket cp = RC_Protocol.decode(packet.getData(), packet.getLength());

                // Chi xu ly neu goi tin nay MOI HON goi gan nhat CUNG LOAI da xu ly truoc do
                // -> day chinh la co che "vut bo goi cu, chi thuc thi lenh/toa do moi nhat"
                if (cp.type >= 0 && cp.type < lastSeqByType.length) {
                    if (cp.seq <= lastSeqByType[cp.type]) {
                        continue; // goi den tre hon (do nghen mang) hoac trung lap -> bo qua
                    }
                    lastSeqByType[cp.type] = cp.seq;

                    // Neu la goi thao tac chuot (DOWN / UP), cap nhat luon lastSeq cua TYPE_MOVE
                    // de loai bo cac goi MOVE cu hon da gui truoc cu click nhung den muon
                    if (cp.type == RC_Protocol.TYPE_MOUSE_DOWN || cp.type == RC_Protocol.TYPE_MOUSE_UP) {
                        if (cp.seq > lastSeqByType[RC_Protocol.TYPE_MOVE]) {
                            lastSeqByType[RC_Protocol.TYPE_MOVE] = cp.seq;
                        }
                    }
                }

                executeCommand(cp);
            }
        } catch (IOException e) {
            if (running.get()) console.println("[Host] Lỗi UDP control server: " + e.getMessage());
        }
    }

    // Thuc thi lenh da nhan duoc, bang java.awt.Robot
    private void executeCommand(RC_Protocol.ControlPacket cp) {
        switch (cp.type) {
            case RC_Protocol.TYPE_MOVE:
                robot.mouseMove(cp.p1, cp.p2);
                break;
            case RC_Protocol.TYPE_MOUSE_DOWN:
                robot.mouseMove(cp.p2, cp.p3);
                robot.mousePress(toInputEventMask(cp.p1));
                break;
            case RC_Protocol.TYPE_MOUSE_UP:
                robot.mouseMove(cp.p2, cp.p3);
                robot.mouseRelease(toInputEventMask(cp.p1));
                break;
            case RC_Protocol.TYPE_WHEEL:
                robot.mouseWheel(cp.p1);
                break;
            case RC_Protocol.TYPE_KEY_DOWN:
                robot.keyPress(cp.p1);
                break;
            case RC_Protocol.TYPE_KEY_UP:
                robot.keyRelease(cp.p1);
                break;
            default:
                break;
        }
    }

    private int toInputEventMask(int buttonCode) {
        switch (buttonCode) {
            case RC_Protocol.BUTTON_LEFT:   return InputEvent.BUTTON1_DOWN_MASK;
            case RC_Protocol.BUTTON_MIDDLE: return InputEvent.BUTTON2_DOWN_MASK;
            case RC_Protocol.BUTTON_RIGHT:  return InputEvent.BUTTON3_DOWN_MASK;
            default: return InputEvent.BUTTON1_DOWN_MASK;
        }
    }
}
