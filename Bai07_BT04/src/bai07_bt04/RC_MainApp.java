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
import java.io.PrintStream;
import javax.swing.*;

/**
 * RC_MainApp.java  -  BAI07_BT04: Dieu khien chuot/phim tu xa (Remote Controller)
 *
 * Giao dien chinh - CHAY GIONG HET NHAU tren ca may A va may B.
 * Moi may co the dong thoi:
 *  - Bat "Cho phep may khac dieu khien minh" (dong vai HOST) - luon san sang o nen.
 *  - "Dieu khien mot may khac" bang cach nhap IP cua may do (dong vai CONTROLLER),
 *    mo ra 1 cua so hien thi man hinh may do va cho phep dieu khien ngay tren do.
 *
 * Nho chay chung 1 chuong trinh tren ca 2 may, A co the dieu khien B,
 * va B cung co the quay lai dieu khien A - tuy vao ai nhap IP va bam nut truoc,
 * giong cach hoat dong 2 chieu cua AnyDesk/UltraViewer.
 *
 * Cach chay:
 *   java Bai07.RC_MainApp
 */
public class RC_MainApp extends JFrame {
    private RC_HostServer hostServer;
    private JTextArea logArea;
    private JButton btnToggleHost;
    private JTextField ipField;

    public RC_MainApp() {
        super("Remote Controller - BAI07_BT04");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(560, 420);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JPanel topPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        btnToggleHost = new JButton("Bật: Cho phép máy khác điều khiển mình");
        btnToggleHost.addActionListener(e -> toggleHost());
        topPanel.add(btnToggleHost);

        JPanel connectPanel = new JPanel(new BorderLayout(5, 5));
        ipField = new JTextField("127.0.0.1");
        JButton btnConnect = new JButton("Điều khiển máy này");
        btnConnect.addActionListener(e -> connectAsController());
        connectPanel.add(new JLabel("Nhập IP máy muốn điều khiển:"), BorderLayout.NORTH);
        connectPanel.add(ipField, BorderLayout.CENTER);
        connectPanel.add(btnConnect, BorderLayout.EAST);
        topPanel.add(connectPanel);

        JLabel myIpLabel = new JLabel("Địa chỉ IP của máy này (để máy kia nhập vào): đang dò...");
        topPanel.add(myIpLabel);
        new Thread(() -> {
            try {
                String ip = java.net.InetAddress.getLocalHost().getHostAddress();
                SwingUtilities.invokeLater(() -> myIpLabel.setText("Địa chỉ IP của máy này: " + ip));
            } catch (Exception ignored) {
            }
        }).start();

        add(topPanel, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        setVisible(true);
    }

    // Bat/tat vai tro Host (cho phep may khac dieu khien minh)
    private void toggleHost() {
        try {
            if (hostServer == null) {
                PrintStream logStream = new PrintStream(new TextAreaOutputStream(logArea), true, "UTF-8");
                hostServer = new RC_HostServer(logStream);
                hostServer.start();
                btnToggleHost.setText("Tắt: Đang cho phép máy khác điều khiển mình");
            } else {
                hostServer.stop();
                hostServer = null;
                btnToggleHost.setText("Bật: Cho phép máy khác điều khiển mình");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi khởi động Host: " + e.getMessage());
        }
    }

    // Mo cua so dieu khien may khac theo IP nguoi dung nhap
    private void connectAsController() {
        String ip = ipField.getText().trim();
        if (ip.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập IP máy cần điều khiển.");
            return;
        }
        new RC_ControllerWindow(ip);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(RC_MainApp::new);
    }
}
