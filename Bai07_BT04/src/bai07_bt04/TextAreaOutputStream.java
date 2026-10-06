/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package bai07_bt04;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */


import java.io.OutputStream;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * TextAreaOutputStream.java
 * Lop phu: cho phep dung console.println(...) nhu binh thuong nhung noi dung se
 * duoc hien thi truc tiep trong 1 JTextArea cua giao dien, thay vi ra cua so Output cua NetBeans.
 */
class TextAreaOutputStream extends OutputStream {
    private final JTextArea textArea;
    private final StringBuilder lineBuffer = new StringBuilder();

    TextAreaOutputStream(JTextArea textArea) {
        this.textArea = textArea;
    }

    @Override
    public void write(int b) {
        lineBuffer.append((char) b);
        if (b == '\n') {
            String text = lineBuffer.toString();
            SwingUtilities.invokeLater(() -> {
                textArea.append(text);
                textArea.setCaretPosition(textArea.getDocument().getLength());
            });
            lineBuffer.setLength(0);
        }
    }
}