package com.example.day0426;

import java.io.*;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
    private Map<Integer, Room> rooms;
    private List<Integer> roomNumbers;
    int myRoomNumber = 0; // 0이면 방 밖에 있다는 뜻. 번호가 있으면 그 번호의 방안에 있다는 뜻.
    BufferedReader in;
    PrintWriter out;
    File chatLog;

    public ChatThreadProject(Socket socket, Map<String, PrintWriter> chatClients, Map<Integer, Room> rooms, List<Integer> roomNumbers) {
        this.socket = socket;
        this.chatClients = chatClients;
        this.rooms = rooms;
        this.roomNumbers = roomNumbers;

        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // 닉네임 중복 검사
            while (true) {
                nickname = in.readLine();
                if (chatClients.containsKey(nickname)) {
                    out.println("이미 있는 닉네임입니다. 다른 닉네임을 입력해주세요.");
                    out.println("Enter your nickname: ");
                } else {
                    break;
                }
            }

            System.out.println("새로운 사용자의 id : " + nickname);

            // put 작업이 동시에 일어날 수도 있음
            synchronized (chatClients) {
                chatClients.put(nickname, out);
            }

            out.println("/help로 명령어들을 볼 수 있습니다.");

            this.chatLog = new File("src/com/example/day0426/" + nickname + ".log");

            if (!chatLog.exists()) {
                chatLog.createNewFile();
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

                // help : 명령어들 보여주기
                if ("/help".equalsIgnoreCase(message)) {
                    out.println("/bye : 종료하기\n" +
                            "/create : 방 생성하기\n" +
                            "/list : 방 목록 보기\n" +
                            "/join 방번호 : 방에 들어가기\n" +
                            "/exit : 방 나가기\n" +
                            "/users : 서버에 있는 유저들을 보여주기\n" +
                            "/roomusers : 방에 있는 유저들을 보여주기\n" +
                            "/w 닉네임 메시지 : 해당 유저에게만 메시지 보내기");
                    continue;
                }

                // bye : 종료
                if ("/bye".equalsIgnoreCase(message)) {
                    out.println("종료합니다.");
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
                        synchronized (rooms) {
                            rooms.get(myRoomNumber).leave(nickname);
                        }

                        // 방에 있는 유저들에게 나갔다고 알리기
                        for (String nick : rooms.get(myRoomNumber).users) {
                            chatClients.get(nick).println(nickname + "님이 나갔습니다.");
                        }

                        out.println("로비로 나왔습니다.");

                        if (rooms.get(myRoomNumber).users.isEmpty()) {
                            synchronized (rooms) {
                                rooms.remove(myRoomNumber);
                            }
                            synchronized (roomNumbers) {
                                roomNumbers.remove(Integer.valueOf(myRoomNumber));
                            }// myRoomNumber를 인덱스가 아닌 값으로 인식
                        }

                        myRoomNumber = 0;
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
            chatClients.get(nick).println(nickname + " : " + message);
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
            for (String nick : rooms.get(myRoomNumber).users) {
                chatClients.get(nick).println(nickname + "님이 들어왔습니다. ");
            }
        } else {
            out.println("존재하지 않는 방입니다.");
        }
    }
}
