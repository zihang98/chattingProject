package com.example.day0426;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

class Room {
    public List<String> users = new LinkedList<>();
    private int roomNumber;

    public Room(int roomNumber) {
        this.roomNumber = roomNumber;
    }

    public void enter(String userName) {
        synchronized (users) {
            users.add(userName);
        }
    }

    public void leave(String userName) {
        synchronized (users) {
            users.remove(userName);
        }
    }
}

public class ChatThreadProject extends Thread {
    private Socket socket;
    private String nickname;
    private Map<String, PrintWriter> chatClients;
    private Map<Integer, Room> rooms = new HashMap<>();
    private List<Integer> roomNumbers = new LinkedList<>();
    int myRoomNumber = 0; // 0이면 방 밖에 있다는 뜻. 번호가 있으면 그 번호의 방안에 있다는 뜻.
    BufferedReader in;
    PrintWriter out;

    public ChatThreadProject(Socket socket, Map<String, PrintWriter> chatClients) {
        this.socket = socket;
        this.chatClients = chatClients;

        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            nickname = in.readLine();
            //speakInRoom(nickname + "님이 입장");
            System.out.println("새로운 사용자의 id : " + nickname);

            // put 작업이 동시에 일어날 수도 있음
            synchronized (chatClients) {
                chatClients.put(nickname, out);
            }

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    @Override
    public void run() {
        String message = null;
        try {
            while ((message = in.readLine()) != null) {
                String[] messageArray = message.split(" ", 3);

                // bye : 종료
                if ("/bye".equalsIgnoreCase(message)) {
                    break;
                }

                // create : 방 생성하고 그 방에 유저를 입장시킴
                if ("/create".equalsIgnoreCase(message)) {
                    int roomNumber = createRoom();
                    out.println("방이 생성되었습니다.");
                    joinRoom(roomNumber);
                    continue;
                }

                // list : 방 목록
                if ("/list".equalsIgnoreCase(message)) {
                    listRoom();
                    continue;
                }

                // join 방번호 : 방 입장
                if ("/join".equalsIgnoreCase(messageArray[0])) {
                    if (myRoomNumber == 0) {
                        int roomNumber = Integer.parseInt(messageArray[1]);
                        joinRoom(roomNumber);
                    } else {
                        out.println("로비에서만 사용할 수 있습니다.");
                    }
                    continue;
                }

                // exit : 방 나가기. 방을 나가서 방에 있는 유저의 수가 0이 되면 방을 삭제함
                if ("/exit".equalsIgnoreCase(message)) {
                    if (myRoomNumber != 0) {
                        rooms.get(myRoomNumber).leave(nickname);

                        if (rooms.get(myRoomNumber).users.isEmpty()) {
                            rooms.remove(myRoomNumber);
                            roomNumbers.remove(Integer.valueOf(myRoomNumber)); // myRoomNumber를 인덱스가 아닌 값으로
                        }

                        myRoomNumber = 0;
                        out.println("로비로 나왔습니다.");
                    } else {
                        out.println("방 안이 아닙니다.");
                    }

                    continue;
                }

                // users : 서버에 있는 모든 유저들을 보여줌
                if ("/users".equalsIgnoreCase(message)) {
                    out.println(chatClients.keySet());
                    continue;
                }

                // roomusers : 나와 방에 있는 유저 목록을 보여줌
                if ("/roomusers".equalsIgnoreCase(message)) {
                    if (myRoomNumber != 0) {
                        out.println(myRoomNumber + "번 방 안에 있는 유저들 : " + rooms.get(myRoomNumber).users);
                    } else {
                        out.println("방 안이 아닙니다.");
                    }

                    continue;
                }

                // w 닉네임 메시지 : 귓속말 기능. 닉네임에게만 메시지를 보낸다.
                if ("/w".equalsIgnoreCase(messageArray[0])) {
                    if (chatClients.containsKey(messageArray[1])) {
                        System.out.println("messageArray : " + Arrays.toString(messageArray));
                        chatClients.get(messageArray[1]).println(nickname + "의 귓말 : " + messageArray[2]);
                    } else {
                        chatClients.get(nickname).println("접속해있지 않은 사용자입니다.");
                    }
                    continue;
                }

                // 채팅 치기. 방 안이면 채팅이 쳐지고 아니면 방 안이 아니라고 알려줌.
                if (myRoomNumber != 0) {
                    speakInRoom(message);
                    continue;
                } else {
                    out.println("로비에서는 채팅을 할 수 없습니다.");
                    continue;
                }


//                // 귓말
//                if ("/to".equalsIgnoreCase(messageArray[0])) {
//                    if (chatClients.containsKey(messageArray[1])) {
//                        System.out.println("messageArray : " + Arrays.toString(messageArray));
//                        chatClients.get(messageArray[1]).println(nickname + "의 귓말 : " + messageArray[2]);
//                    } else {
//                        chatClients.get(nickname).println("접속해있지 않은 사용자입니다");
//                    }
//                    continue;
//                }

//                speakInRoom(nickname + " : " + message);
            }

        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        } finally {
            synchronized (chatClients) {
                chatClients.remove(nickname);
            }

            broadCast(nickname + "님이 나갔음");

            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void speakInRoom(String message) {
        for (String nick : rooms.get(myRoomNumber).users) {
            chatClients.get(nick).println(message);
        }
    }

    public void broadCast(String message) {
        for (PrintWriter out : chatClients.values()) {
            out.println(message);
        }
    }

    public int createRoom() {
        int roomNumber = 1;
        while (true) {
            if (roomNumbers.contains(roomNumber)) {
                roomNumber++;
            } else {
                roomNumbers.add(roomNumber);
                break;
            }
        }
        rooms.put(roomNumber, new Room(roomNumber));

        return roomNumber;
    }

    public void listRoom() {
        out.println("현재 있는 방들 : " + roomNumbers);
    }

    public void joinRoom(int roomNumber) {
        if (roomNumbers.contains(roomNumber)) {
            rooms.get(roomNumber).enter(nickname);
            myRoomNumber = roomNumber;
            out.println(myRoomNumber + "번 방에 들어왔습니다.");
        } else {
            out.println("존재하지 않는 방입니다.");
        }
    }

//    public void exitRoom() {
//
//    }

}
