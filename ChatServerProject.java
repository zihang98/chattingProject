package com.example.day0426;

import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ChatServerProject {
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(12345);) {
            System.out.println("서버가 준비됨");

            Map<String, PrintWriter> chatClients = new HashMap<>();
            Map<Integer, Room> rooms = new HashMap<>();
            List<Integer> roomNumbers = new LinkedList<>();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ChatThreadProject chatThread = new ChatThreadProject(clientSocket, chatClients, rooms, roomNumbers);
                chatThread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
