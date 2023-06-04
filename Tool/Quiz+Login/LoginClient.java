import java.io.*;
import java.net.*;
import java.util.Arrays;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class LoginClient extends Application {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 8080;

    private BufferedReader in;
    private PrintWriter out;

    // ログイン用
    private TextField usernameField;
    private PasswordField passwordField;
    private Label messageLabel;

    // 登録用
    private TextField registrationUsernameField;
    private PasswordField registrationPasswordField;
    private Label registrationMsgLabel;

    //問題表示用
    private TextArea questionArea;
    private TextField answerField;
    private Label scoreLabel;
    private Button startButton;
    private int score = 0;


    //ログインしたユーザー名
    private String myusername;
    private int totalscore;

    //スコア表示用
    private Button nextButton;


    



    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        final Stage finalPrimaryStage = primaryStage;

        primaryStage.setTitle("Login & Registration");

        // ログイン部分
        Label loginLabel = new Label("Login");
        Label usernameLabel = new Label("Username");
        usernameField = new TextField();
        Label passwordLabel = new Label("Password");
        passwordField = new PasswordField();
        Button loginButton = new Button("Login");
        messageLabel = new Label();

        loginButton.setOnAction(e -> {
            String username = usernameField.getText();
            String password = passwordField.getText();

            // どちらかが入力されていないとき
            if (username.isEmpty() || password.isEmpty()) {
                messageLabel.setText("Please enter username and password");
                return;
            }

            // 入力情報をサーバーに送信
            out.println("LOGIN_c " + username + " " + password);
        });

        VBox loginLayout = new VBox(10);
        loginLayout.getChildren().addAll(loginLabel,usernameLabel, usernameField, passwordLabel, passwordField, loginButton, messageLabel);
        loginLayout.setAlignment(Pos.CENTER);
        loginLayout.setPadding(new Insets(10));

        // 登録部分
        Label registrationLabel = new Label("Registration");
        Label registrationUsernameLabel = new Label("Username");
        registrationUsernameField = new TextField();
        Label registrationPasswordLabel = new Label("Password");
        registrationPasswordField = new PasswordField();
        Button registrationButton = new Button("Register");
        registrationMsgLabel = new Label();

        registrationButton.setOnAction(e -> {
            String username = registrationUsernameField.getText();
            String password = registrationPasswordField.getText();

            // どちらかが入力されていないとき
            if (username.isEmpty() || password.isEmpty()) {
                registrationMsgLabel.setText("Please enter username and password");
                return;
            }

            // 入力情報をサーバーに送信
            out.println("REGISTER_c " + username + " " + password);
        });

        VBox registrationLayout = new VBox(10);
        registrationLayout.getChildren().addAll(registrationLabel,registrationUsernameLabel, registrationUsernameField, registrationPasswordLabel, registrationPasswordField, registrationButton, registrationMsgLabel);
        registrationLayout.setAlignment(Pos.CENTER);
        registrationLayout.setPadding(new Insets(10));

        // ログインルートレイアウトの作成
        VBox rootLayout = new VBox(20);
        rootLayout.getChildren().addAll(loginLayout, registrationLayout);
        rootLayout.setAlignment(Pos.CENTER);

        // シーンの作成
        Scene scene = new Scene(rootLayout, 400, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        //クイズ用
        questionArea = new TextArea();
        questionArea.setEditable(false);
        questionArea.setPrefHeight(150);
        questionArea.setText("早押しクイズを始めます。\n\n一番早く正しい回答を送った参加者に点数を与えます。\nクイズは全5問です。\n\n\nStartを押して開始してください。");
        answerField = new TextField();
        answerField.setPromptText("Press the start button");
        //answerField.setPromptText("Enter your answer...");
        scoreLabel = new Label("Score: 0");
        startButton = new Button("Start");
        startButton.setOnAction(e -> sendStartSignal());

        VBox root = new VBox(10,questionArea,answerField,scoreLabel,startButton);
        root.setPadding(new Insets(10));
        root.setAlignment(Pos.CENTER);

        //クイズ用のシーン
        Scene sceneQ = new Scene(root, 400, 300);
        //CSSファイルを読み込む
        sceneQ.getStylesheets().add(getClass().getResource("color.css").toExternalForm());

        ///スコア表示用
        nextButton = new Button("Next");
        nextButton.setOnAction(e -> sendNextSignal());


        connectToServer();

        //回答をサーバーに送信し、回答エリアをリセット
        answerField.setOnAction(
            e -> {
                String answer = answerField.getText().trim();
                out.println("ANSWER_c " + myusername + " " + answer);
                answerField.clear();
            }
        );

        new Thread(() -> {
            try {
                String message;
                while ((message = in.readLine()) != null) {

                    // ログイン成功時
                    if (message.startsWith("SUCCESS_s")) {

                        //ユーザー名を保持
                        myusername = message.substring(10);
                        System.out.println(myusername);


                        Platform.runLater(() -> {
                            //メッセージを表示して
                            messageLabel.setText("Login successful");

                            //クイズ画面を表示
                            finalPrimaryStage.setScene(sceneQ);
                            primaryStage.setTitle(myusername);
                            
                        });

                    // ログイン失敗時
                    } else if (message.startsWith("FAILURE_s")) {
                        Platform.runLater(() -> {
                            messageLabel.setText("Login failed");
                        });

                    // 登録成功時
                    } else if (message.startsWith("REG_SUCCESS_s")) {
                        Platform.runLater(() -> {
                            registrationMsgLabel.setText("Registration successful");
                            registrationUsernameField.clear();
                            registrationPasswordField.clear();
                        });

                    // 登録失敗時
                    } else if (message.startsWith("REG_FAILURE_s")) {
                        Platform.runLater(() -> {
                            registrationMsgLabel.setText("Registration failed");
                        });

                    //特殊文字（空白、カンマ）
                    }else if(message.startsWith("REG_CANNOTUSE_s")){

                        Platform.runLater(() -> {
                            registrationMsgLabel.setText("No spaces or commas allowed");
                        });
                    
                    //受信したメッセージが"QUESTION "で始まる場合
                    }else if (message.startsWith("QUESTION_s ")) {
                        startButton.setDisable(true);
                        //root.getChildren().remove(startButton);

                        String[] parts = message.split(" ");
                        String questionCount = parts[1];
                        String[] questionParts = Arrays.copyOfRange(parts, 2, parts.length);

                        String question = String.join(" ", questionParts);
                        questionArea.setText(questionCount + "問目\n" + question);

                        answerField.setPromptText("Enter your answer....");
                            
                        //回答入力フィールドを有効化
                        answerField.setDisable(false);
                        
                    //受信したメッセージが"CORRECT_s  "で始まる場合
                    } else if (message.startsWith("CORRECT_s ")) {
                        
                        //回答入力フィールドが無効化
                        answerField.setDisable(true);
        
                        //スコアが更新
                        //"CORRECT_s"以降(10文字目から)の得点をscoreとする
                        score = Integer.parseInt(message.substring(10));
        
                        //スコア表示の更新をJavaFXのUIスレッドで行う
                        Platform.runLater(() -> {
                        // スコア表示の更新
                        scoreLabel.setText("Score: " + score);
                        });
        
                    //受信したメッセージが"ANSWER_s "で始まる場合
                    }else if(message.startsWith("ANSWER_s ")){

                        String[] parts = message.split(" ");
                        String correctusername = parts[1];

                        String[] answerParts = Arrays.copyOfRange(parts, 2, parts.length);
                        String correctanswer = String.join(" ", answerParts);
                        
                        //正答を表示
                        questionArea.setText(correctusername + "が正解しました\n正解は\n\n" + correctanswer + "\n\nです");
                        
                        //回答入力フィーfinalsルドが無効化
                        answerField.setDisable(true);
        
                    //受信したメッセージが"WRONG_s "で始まる場合
                    }else if(message.startsWith("WRONG_s ")){
                        //今の問題を保管して
                        String currentText = questionArea.getText();
                        //間違いのメッセージを追加して表示
                        String appendedText = currentText + "\n\nあなたの回答は間違いです！\n正しい回答を入力してください。";
                        questionArea.setText(appendedText);

                    ///トータルスコアの提示
                    }else if(message.startsWith("YOURTOTALSCORE_s ")){

                        System.out.println("your totalscore received");

                        totalscore = Integer.parseInt(message.substring(17));
                        questionArea.clear();
                        questionArea.setText("\n\nあなたのトータルスコアは"+Integer.toString(totalscore)+"です");

                    //ファイナルスコアの表示
                    } else if (message.startsWith("FINALSCORE_s ")) {
                        System.out.println(message);

                        String allfinalscore = "";

                        if (message.length() > 13) {
                            String finalscore = message.substring(13).replace("\\n", "\n");
                            allfinalscore += finalscore;
                        }

                        questionArea.clear();
                        questionArea.setText("最終結果\n"+allfinalscore);
                        System.out.println(allfinalscore);


                        ///ボタンを押すと自分のトータルスコアを確認できる
                        Platform.runLater(() -> {
                            root.getChildren().add(nextButton);
                        });

                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    //ゲームの開始シグナルをサーバーに送信するメソッド
    private void sendStartSignal() {
        System.out.println("sendStartSignal");
        ///サーバーに"START"メッセージを送信
        out.println("START_c ");
    }


    ///nextボタンでトータルスコア表示
    private void sendNextSignal(){
        System.out.println("sendNextSignal");
        out.println("MYTOTALSCORE_c "+ myusername);
    }

    //サーバーへの接続を確立するメソッド
    private void connectToServer() {
        try {
            Socket socket = new Socket(SERVER_IP, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}