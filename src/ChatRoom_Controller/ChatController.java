package ChatRoom_Controller;

import chatRoom_Model.ChatClient;
import chatRoom_Model.*;
import chatRoom_View.ChatLogin;
import chatRoom_View.ChatWindow;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;

public class ChatController implements ActionListener {
    private ChatLogin chatLogin;
    private String ifLogin;

    private ChatWindow chatWindow;
    private String nickName;
    private String groupName;
    private MessageMap onlineList;
    private MessageMap myMessage;

    private ObjectOutputStream oos;
    private ObjectInputStream ois;
    private ChatClient client;
    private Socket socket;

    private ArrayList<MessagePane> messagePaneList;
    private String senderName;
    private Timer autoUpdate;
    private String latestDisconnection;
    private String localLatestDisconnection;
    private UdpReceiver udpReceiver;

    private  static DatagramSocket clientUdp;
    // constructors

    // constructor for ChatLogin
    public ChatController(ChatLogin chatLogin, ObjectOutputStream oos, ObjectInputStream ois) {
        this.chatLogin = chatLogin;
        this.oos = oos;
        this.ois = ois;
        this.ifLogin = "duplicated";
        this.chatLogin.getLoginButton().addActionListener(this);
    }

    // constructor for ChatWindow
    public ChatController(ChatWindow chatWindow, String nickName, String groupName, MessageMap onlineList,
                          MessageMap myMessage, ObjectOutputStream oos, ObjectInputStream ois, ChatClient client, Socket socket, int your_Port) {
        this.chatWindow = chatWindow;
        this.nickName = nickName;
        this.groupName = groupName;
        this.onlineList = onlineList;
        this.myMessage = myMessage;
        this.oos = oos;
        this.ois = ois;
        this.client = client;

        this.autoUpdate = new Timer();


        this.chatWindow.getsendButton().addActionListener(this);
        this.chatWindow.getTerminate().addActionListener(this);

         udpReceiver = new UdpReceiver(chatWindow, client, autoUpdate, your_Port, oos);
         udpReceiver.start();

    }

    // for ChatLogin functions
    public String getNickName() {
        while (this.nickName == null) {
            Thread.onSpinWait();
        }
        return this.nickName;
    }
    public String getGroupName(){
        return this.groupName;
    }


    private void updateList() {
        try {
            this.oos.writeObject("UpdateList");
            System.out.println((String) ois.readObject());
            MessageMap temp;
            temp = (MessageMap) (ois.readObject());

            latestDisconnection = (String)(ois.readObject());

           if(!this.onlineList.containsKey(latestDisconnection)){
               latestDisconnection = null;
           }
            if(latestDisconnection != null) {
                this.chatWindow.getsendButton().setEnabled(false);


                if (this.onlineList.containsKey(latestDisconnection)) {
                    onlineList.remove(latestDisconnection);
                    temp.remove(latestDisconnection);
                    JOptionPane.showMessageDialog(this.chatWindow, latestDisconnection + " has disconnected", "Notice",JOptionPane.WARNING_MESSAGE);
                    if(latestDisconnection.equals(senderName)){
                        senderName = null;
                    }
                }
            }
            Stack<String> userStack = new Stack<>();
            for(String user : onlineList.keySet()){
                if(!temp.containsKey(user)){
                    userStack.push(user);
                }
            }
            for(String user : temp.keySet()){
                userStack.push(user);
            }
            // Todo: update myMessageMap
            // update local onlineList
            for (String sender : temp.keySet()) {
                if (!onlineList.containsKey(sender)) {
                    onlineList.put(sender, new Stack<String>());
                    myMessage.put(sender, new Stack<String>());
                }
                while(!temp.get(sender).isEmpty()) {
                    onlineList.get(sender).push(temp.get(sender).pop());
                    myMessage.get(sender).push(null);
                }
            }
            // avoid errors from first client
            if(!userStack.isEmpty()) {
                messagePaneList = chatWindow.showMessageList(userStack, onlineList, myMessage);
                for(MessagePane messagePane: messagePaneList){
                    messagePane.getMessageButton().addActionListener(this);
                }
            }
                updateHistory();
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Exception occurs " + e);

        }
    }




    // for Chatwindow functions
    public void autoUpdateList() {
        autoUpdate.schedule(new TimerTask() {
            @Override
            public void run() {
                updateList();
            }
        }, 1000, 5000);
    }

    private void updateHistory(){
        if(latestDisconnection != senderName) {
            Stack<String> sendMessageList = new Stack<>();
            Stack<String> receiveMessageList = new Stack<>();
            if (senderName != null) {
                this.chatWindow.setSenderLabel(this.groupName + "(" + onlineList.keySet().size() +")" + ": " + senderName);
                if (!onlineList.get(senderName).isEmpty()) {
                    for (String receiveMessage : onlineList.get(senderName))
                        receiveMessageList.push(receiveMessage);
                }
                if (!myMessage.get(senderName).isEmpty()) {
                    for (String sendMessage : myMessage.get(senderName))
                        sendMessageList.push(sendMessage);
                }
                this.chatWindow.showHistory(sendMessageList, receiveMessageList);
            }else{
                this.chatWindow.setSenderLabel(this.groupName + "(" + onlineList.keySet().size() +")");
            }
        }
    }
    // one for all controller actionListener
    public void actionPerformed(ActionEvent e) {
        if (this.chatLogin != null) {
            try {
                String inputName = this.chatLogin.getInputNickName().getText();
                String groupNumber = this.chatLogin.getGroup().getText();
                if (inputName.equals("")) {
                    this.chatLogin.getWarning().setText("Nickname should not be null");
                } else {
                    oos.writeObject(inputName);
                    oos.writeObject(groupNumber);
                    this.ifLogin = (String) ois.readObject();
                    if (ifLogin.equals("duplicated")) {
                        this.chatLogin.getWarning().setText("Name already exists");
                    } else {
                        this.nickName = inputName;
                        this.groupName = groupNumber;
                        this.chatLogin.dispose();
                    }
                }
            } catch (IOException | ClassNotFoundException ex) {
                System.out.println("Exception occurs " + ex);
            }

        } else if (e.getSource() == this.chatWindow.getsendButton()) {
            try {
                oos.writeObject("Msg " + this.senderName + " " +this.chatWindow.getMsg());
                myMessage.get(senderName).push(this.chatWindow.getMsg());
                onlineList.get(senderName).push(null);
                this.chatWindow.setTextArea();
                System.out.println((String) ois.readObject());


                updateHistory();

            } catch (IOException | ClassNotFoundException ex) {
                System.err.println("Exception occurs" + ex);
            }
        } else if (e.getSource() == this.chatWindow.getTerminate()) {
            try {
                oos.writeObject("terminate");
                oos.writeObject(this.nickName);
                System.out.println((String) ois.readObject());
                this.oos.close();
                this.ois.close();
                this.chatWindow.dispose();
                this.autoUpdate.cancel();
                this.udpReceiver.getUdpSocket().close();
                this.client.isStopped = true;
            } catch (IOException | ClassNotFoundException ex) {
                System.err.println("Exception occurs: " + ex);
            }
        } else{ // buttonList
            JButton userButton = (JButton)e.getSource();
            this.chatWindow.getsendButton().setEnabled(true);
//            this.chatWindow.clearHisotry();
            for(MessagePane messagePane : messagePaneList){
                // find the sender from onlineList
                if(messagePane.getMessageButton().getText().equals(userButton.getText())){
                    this.senderName = messagePane.getUserName();
                }
            }
            updateHistory();
        }
    }

}

