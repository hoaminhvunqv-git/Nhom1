/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package bai07_bt04;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * RC_ControllerWindow.java
 * Chay vai tro CONTROLLER tren MAY DIEU KHIEN (vi du may A khi A dang dieu khien B),
 * ket noi toi mot RC_HostServer dang chay tren may khac de:
 *
 *  1) Nhan va hien thi hinh anh man hinh cua may do qua TCP.
 *     Hien thi theo co che CO GIAN (SCALE) vua khit kich thuoc cua so,
 *     giu dung ti le khung hinh (aspect ratio) va can giua (letterboxing/pillarboxing)
 *     giong nhu AnyDesk, TeamViewer, UltraViewer - giup thay toan bo man hinh gom ca Taskbar.
 *  2) Bat su kien chuot/ban phim tren khung hinh nay, quy doi toa do tu vung anh da co gian
 *     sang toa do thuc tren man hinh may kia (bo qua vung vien den letterbox),
 *     roi gui lenh dieu khien qua UDP de thuc thi.
 */
public class RC_ControllerWindow extends JFrame {
    private final String hostIp;
    private DatagramSocket udpSocket;
    private InetAddress hostAddress;
    private final AtomicLong seqCounter = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final ScreenPanel screenPanel;
    // Kich thuoc man hinh THAT cua may Host, nhan duoc ngay sau khi ket noi TCP thanh cong
    private int remoteWidth = 1920;
    private int remoteHeight = 1080;

    public RC_ControllerWindow(String hostIp) {
        super("Đang điều khiển: " + hostIp);
        this.hostIp = hostIp;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1000, 650);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Thanh phan hien thi tu ve (custom JPanel) thay the cho JLabel + JScrollPane
        screenPanel = new ScreenPanel(hostIp);
        add(screenPanel, BorderLayout.CENTER);

        JLabel hint = new JLabel("  Di/bấm chuột, gõ phím ngay trên khung hình này để điều khiển máy " + hostIp);
        add(hint, BorderLayout.SOUTH);

        setupInputCapture();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                running.set(false);
                if (udpSocket != null) udpSocket.close();
            }
        });

        setVisible(true);

        try {
            udpSocket = new DatagramSocket();
            hostAddress = InetAddress.getByName(hostIp);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Không thể tạo UDP socket: " + e.getMessage());
        }

        Thread screenThread = new Thread(this::receiveScreenLoop, "ReceiveScreenThread");
        screenThread.setDaemon(true);
        screenThread.start();
    }

    // ===================== NHAN HINH ANH MAN HINH TU HOST (QUA TCP) =====================
    private void receiveScreenLoop() {
        try (Socket socket = new Socket(hostIp, RC_Protocol.TCP_SCREEN_PORT);
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {

            remoteWidth = in.readInt();
            remoteHeight = in.readInt();
            screenPanel.setRemoteResolution(remoteWidth, remoteHeight);

            while (running.get()) {
                int frameLength = in.readInt();
                byte[] frameBytes = new byte[frameLength];
                in.readFully(frameBytes);

                BufferedImage image = ImageIO.read(new ByteArrayInputStream(frameBytes));
                if (image != null) {
                    screenPanel.setFrame(image);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, "Mất kết nối xem màn hình: " + e.getMessage()));
            }
        }
    }

    // ===================== BAT SU KIEN VA GUI LENH DIEU KHIEN (QUA UDP) =====================
    private void setupInputCapture() {
        screenPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) { sendMoveCommand(e); }
            @Override
            public void mouseDragged(MouseEvent e) { sendMoveCommand(e); }
        });

        screenPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                screenPanel.requestFocusInWindow(); // de nhan duoc su kien ban phim ngay sau khi click
                sendMouseButton(e, RC_Protocol.TYPE_MOUSE_DOWN);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                sendMouseButton(e, RC_Protocol.TYPE_MOUSE_UP);
            }
        });

        screenPanel.addMouseWheelListener(e -> {
            Point pt = getRemotePoint(e);
            if (pt == null) return; // Chuot o vien den thi khong cuon
            int amount = e.getWheelRotation() * 3; // he so nhan de cuon muot hon tren man hinh that
            sendPacket(RC_Protocol.TYPE_WHEEL, amount, 0, 0);
        });

        screenPanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                sendPacket(RC_Protocol.TYPE_KEY_DOWN, e.getKeyCode(), 0, 0);
            }
            @Override
            public void keyReleased(KeyEvent e) {
                sendPacket(RC_Protocol.TYPE_KEY_UP, e.getKeyCode(), 0, 0);
            }
        });
    }

    // Quy doi toa do chuot tu KICH THUOC VUNG ANH DANG CO GIAN sang TOA DO THUC tren man hinh Host
    private Point getRemotePoint(MouseEvent e) {
        Rectangle rect = screenPanel.getImageBounds();
        if (rect.width <= 0 || rect.height <= 0) return null;

        int mouseX = e.getX();
        int mouseY = e.getY();

        // 1. Neu diem thao tac roi vao vung vien den (ngoai vung anh thuc), bo qua
        if (mouseX < rect.x || mouseX >= rect.x + rect.width ||
            mouseY < rect.y || mouseY >= rect.y + rect.height) {
            return null;
        }

        // 2. Toa do tuong doi ben trong vung anh dang hien thi
        int imgX = mouseX - rect.x;
        int imgY = mouseY - rect.y;

        // 3. Quy doi sang toa do thuc tren man hinh Host theo ti le
        int remoteX = (int) ((long) imgX * remoteWidth / rect.width);
        int remoteY = (int) ((long) imgY * remoteHeight / rect.height);

        // 4. Gioi han toa do trong pham vi an toan cua man hinh Host
        remoteX = Math.max(0, Math.min(remoteWidth - 1, remoteX));
        remoteY = Math.max(0, Math.min(remoteHeight - 1, remoteY));

        return new Point(remoteX, remoteY);
    }

    private void sendMoveCommand(MouseEvent e) {
        Point pt = getRemotePoint(e);
        if (pt == null) return;
        sendPacket(RC_Protocol.TYPE_MOVE, pt.x, pt.y, 0);
    }

    private void sendMouseButton(MouseEvent e, byte type) {
        Point pt = getRemotePoint(e);
        if (pt == null) return; // Click vao vien den -> bo qua, khong gui lenh

        int button;
        switch (e.getButton()) {
            case MouseEvent.BUTTON1: button = RC_Protocol.BUTTON_LEFT; break;
            case MouseEvent.BUTTON2: button = RC_Protocol.BUTTON_MIDDLE; break;
            case MouseEvent.BUTTON3: button = RC_Protocol.BUTTON_RIGHT; break;
            default: button = RC_Protocol.BUTTON_LEFT;
        }

        sendPacket(type, button, pt.x, pt.y);
    }

    // Dong goi va gui 1 lenh dieu khien qua UDP, kem so thu tu TANG DAN
    // de ben nhan (Host) biet goi nao la moi nhat va vut bo goi cu
    private void sendPacket(byte type, int p1, int p2) {
        sendPacket(type, p1, p2, 0);
    }

    private void sendPacket(byte type, int p1, int p2, int p3) {
        if (udpSocket == null || udpSocket.isClosed()) return;
        try {
            long seq = seqCounter.incrementAndGet();
            byte[] data = RC_Protocol.encode(seq, type, p1, p2, p3);
            DatagramPacket packet = new DatagramPacket(data, data.length, hostAddress, RC_Protocol.UDP_CONTROL_PORT);
            udpSocket.send(packet);
        } catch (IOException ignored) {
        }
    }

    // ===================== CUSTOM JPANEL HIEN THI MAN HINH CO GIAN =====================
    private static class ScreenPanel extends JPanel {
        private BufferedImage currentFrame = null;
        private int remoteWidth = 1920;
        private int remoteHeight = 1080;
        private final String hostIp;

        public ScreenPanel(String hostIp) {
            this.hostIp = hostIp;
            setBackground(Color.BLACK);
            setFocusable(true);
        }

        public synchronized void setRemoteResolution(int width, int height) {
            if (width > 0 && height > 0) {
                this.remoteWidth = width;
                this.remoteHeight = height;
            }
        }

        public synchronized void setFrame(BufferedImage image) {
            this.currentFrame = image;
            repaint();
        }

        /**
         * Tinh toan vung ve anh thuc te ben trong panel sau khi co gian,
         * dam bao giu dung aspect ratio va can giua (letterboxing/pillarboxing).
         */
        public synchronized Rectangle getImageBounds() {
            int pWidth = getWidth();
            int pHeight = getHeight();
            int imgWidth = (currentFrame != null) ? currentFrame.getWidth() : remoteWidth;
            int imgHeight = (currentFrame != null) ? currentFrame.getHeight() : remoteHeight;

            if (pWidth <= 0 || pHeight <= 0 || imgWidth <= 0 || imgHeight <= 0) {
                return new Rectangle(0, 0, 0, 0);
            }

            double scaleX = (double) pWidth / imgWidth;
            double scaleY = (double) pHeight / imgHeight;
            double scale = Math.min(scaleX, scaleY);

            int drawWidth = (int) Math.round(imgWidth * scale);
            int drawHeight = (int) Math.round(imgHeight * scale);

            int drawX = (pWidth - drawWidth) / 2;
            int drawY = (pHeight - drawHeight) / 2;

            return new Rectangle(drawX, drawY, drawWidth, drawHeight);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            BufferedImage img;
            synchronized (this) {
                img = currentFrame;
            }

            if (img == null) {
                g.setColor(Color.WHITE);
                String msg = "Đang kết nối và chờ hình ảnh từ máy " + hostIp + "...";
                FontMetrics fm = g.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(msg)) / 2;
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g.drawString(msg, x, y);
                return;
            }

            Graphics2D g2d = (Graphics2D) g.create();
            // Su dung interpolation bilinear de hinh anh khi scale xuong van ro net, khong bi rang cua
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            Rectangle rect = getImageBounds();
            g2d.drawImage(img, rect.x, rect.y, rect.width, rect.height, null);
            g2d.dispose();
        }
    }
}
