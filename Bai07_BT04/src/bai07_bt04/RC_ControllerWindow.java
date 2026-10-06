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
 *  1) Nhan va hien thi hinh anh man hinh cua may do qua TCP - giong man hinh AnyDesk/UltraViewer.
 *  2) Bat su kien chuot/ban phim NGAY TREN CUA SO HIEN THI nay, quy doi toa do tu cua so
 *     sang toa do thuc tren man hinh may kia, roi gui lenh dieu khien qua UDP de thuc thi.
 */
public class RC_ControllerWindow extends JFrame {
    private final String hostIp;
    private DatagramSocket udpSocket;
    private InetAddress hostAddress;
    private final AtomicLong seqCounter = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final JLabel screenLabel;
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

        screenLabel = new JLabel();
        screenLabel.setHorizontalAlignment(SwingConstants.CENTER);
        screenLabel.setFocusable(true);
        add(new JScrollPane(screenLabel), BorderLayout.CENTER);

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

            while (running.get()) {
                int frameLength = in.readInt();
                byte[] frameBytes = new byte[frameLength];
                in.readFully(frameBytes);

                BufferedImage image = ImageIO.read(new ByteArrayInputStream(frameBytes));
                if (image != null) {
                    ImageIcon icon = new ImageIcon(image);
                    SwingUtilities.invokeLater(() -> {
                        screenLabel.setIcon(icon);
                        screenLabel.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
                        screenLabel.revalidate();
                    });
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
        screenLabel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) { sendMoveCommand(e); }
            @Override
            public void mouseDragged(MouseEvent e) { sendMoveCommand(e); }
        });

        screenLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                screenLabel.requestFocusInWindow(); // de nhan duoc su kien ban phim ngay sau khi click
                sendMouseButton(e, RC_Protocol.TYPE_MOUSE_DOWN);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                sendMouseButton(e, RC_Protocol.TYPE_MOUSE_UP);
            }
        });

        screenLabel.addMouseWheelListener(e -> {
            int amount = e.getWheelRotation() * 3; // he so nhan de cuon muot hon tren man hinh that
            sendPacket(RC_Protocol.TYPE_WHEEL, amount, 0);
        });

        screenLabel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                sendPacket(RC_Protocol.TYPE_KEY_DOWN, e.getKeyCode(), 0);
            }
            @Override
            public void keyReleased(KeyEvent e) {
                sendPacket(RC_Protocol.TYPE_KEY_UP, e.getKeyCode(), 0);
            }
        });
    }

    // Quy doi toa do chuot tu KICH THUOC CUA SO HIEN THI sang TOA DO THUC tren man hinh Host
    private Point getRemotePoint(MouseEvent e) {
        Icon icon = screenLabel.getIcon();
        if (icon == null) return null;
        int displayedWidth = icon.getIconWidth();
        int displayedHeight = icon.getIconHeight();
        if (displayedWidth <= 0 || displayedHeight <= 0) return null;

        int remoteX = e.getX() * remoteWidth / displayedWidth;
        int remoteY = e.getY() * remoteHeight / displayedHeight;

        // Gioi han toa do trong pham vi man hinh thuc cua Host
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
        int button;
        switch (e.getButton()) {
            case MouseEvent.BUTTON1: button = RC_Protocol.BUTTON_LEFT; break;
            case MouseEvent.BUTTON2: button = RC_Protocol.BUTTON_MIDDLE; break;
            case MouseEvent.BUTTON3: button = RC_Protocol.BUTTON_RIGHT; break;
            default: button = RC_Protocol.BUTTON_LEFT;
        }
        Point pt = getRemotePoint(e);
        int remoteX = (pt != null) ? pt.x : 0;
        int remoteY = (pt != null) ? pt.y : 0;
        sendPacket(type, button, remoteX, remoteY);
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
}
