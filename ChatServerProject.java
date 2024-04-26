package com.example.day0426;

import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class ChatServerProject {
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(12345);) {
            System.out.println("서버가 준비됨");

            Map<String, PrintWriter> chatClients = new HashMap<>();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ChatThreadProject chatThread = new ChatThreadProject(clientSocket, chatClients);
                chatThread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
