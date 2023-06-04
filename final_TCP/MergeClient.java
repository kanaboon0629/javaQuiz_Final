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

import javafx.scene.control.ScrollPane.ScrollBarPolicy;

public class MergeClient extends Application {
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
  private int questionCount = 0;
  private int quizlimitation = 5;
  private Button raiseHandButton;
  private TextArea chatArea;
  private TextField chatField;
  ///
  private ScrollPane chatScrollPane;


  //ログインしたユーザー名
  private String myusername;
  private int totalscore;

  private boolean done = false; 
  private String fullQuestion;
  private volatile int pauseIndex = 0;
  private volatile boolean incorrectAnswerReceived = false;
  private volatile boolean paused = false;
  private volatile boolean resumeQuestion = false;
  private Thread questionThread = null;

  // チャットエリアを更新するメソッド
  private void updateChatArea(String message) {
    Platform.runLater(() -> {
      String currentText = chatArea.getText();
      String appendedText = currentText + "\n" + message;
      chatArea.setText(appendedText);

      // スクロールバーを最下部に移動
      chatArea.positionCaret(chatArea.getLength()); // テキストエリアの最後にカーソルを移動
      chatArea.selectPositionCaret(chatArea.getLength()); // カーソル位置から最後までテキストを選択
      chatArea.deselect(); // 選択を解除
    });
  }


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
        scoreLabel = new Label("Score: 0");
        startButton = new Button("Start");
        startButton.setOnAction(e -> sendStartSignal());
        raiseHandButton = new Button("Raise Hand");

        raiseHandButton.setOnAction(
          e -> {
            answerField.setDisable(false);
            done = true;
            out.println("RAISEHAND_c");

            if (questionThread != null && questionThread.isAlive()) {
              synchronized (questionThread) {
                questionThread.interrupt();
                pauseIndex = questionArea.getText().length();
              }
              resumeQuestion = true;
            }
          }
        );
        raiseHandButton.setDisable(true);
        Button end = new Button("end");
        end.setOnAction(e->{out.println("END_c");System.exit(1);});

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setPrefHeight(150);

        /// ScrollPaneの設定
        chatScrollPane = new ScrollPane(chatArea);
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setVbarPolicy(ScrollBarPolicy.ALWAYS);
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setVbarPolicy(ScrollBarPolicy.ALWAYS);
        chatScrollPane.setVisible(true);

        chatField = new TextField();
        chatField.setDisable(false);
        chatField.setPromptText(
        "Chat about something and see other clients' chats."
    );
    chatField.setOnAction(
      e->{
        
        String chatmessage = chatField.getText().trim();
        out.println("CHAT_c" + chatmessage);
        chatField.clear();
      }
    );

    answerField.focusedProperty().addListener(
        (observable, oldValue, newValue) -> {
          if (!newValue) {
            if (!answerField.isDisabled() && resumeQuestion) {
              startQuestionThread();
              answerField.setDisable(true);
              resumeQuestion = false;
            }
          }
        }
      );

        VBox root = new VBox(10,
        questionArea,
        answerField,
        scoreLabel,
        startButton,
        raiseHandButton,
        chatArea,
        chatField,
        end);
        root.setPadding(new Insets(10));
        root.setAlignment(Pos.CENTER);

        //クイズ用のシーン
        Scene sceneQ = new Scene(root, 400, 600);
        //CSSファイルを読み込む
        sceneQ.getStylesheets().add(getClass().getResource("color.css").toExternalForm());


        connectToServer();

        //回答をサーバーに送信し、回答エリアをリセット
        answerField.setOnAction(
            e -> {
                String answer = answerField.getText().trim();
                out.println("ANSWER_c " + myusername + " " + answer);
                answerField.clear();
                answerField.setDisable(true);
                raiseHandButton.setDisable(true);
                incorrectAnswerReceived = false; // Add this line
            }
        );

        new Thread(() -> {
            try {
                end.setDisable(true);
                String message;
                while ((message = in.readLine()) != null) {

                    // ログイン成功時
                    if (message.startsWith("SUCCESS_s")) {

                        //ユーザー名を保持
                        myusername = message.substring(10);
                        System.out.println(myusername);
                        out.println("USERNAME_c "+myusername);

                        Platform.runLater(() -> {
                            //メッセージを表示して
                            messageLabel.setText("Login successed");

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
                            registrationMsgLabel.setText("Registration succeeded");
                            registrationUsernameField.clear();
                            registrationPasswordField.clear();
                        });

                    // 登録失敗時
                    } else if (message.startsWith("REG_FAILURE_s")) {
                        Platform.runLater(() -> {
                            registrationMsgLabel.setText("Registration failed");
                        });
                    
                    //受信したメッセージが"QUESTION "で始まる場合
                    }else if (message.startsWith("QUESTION_s ")) {
                        answerField.setDisable(true);
                        startButton.setDisable(true);
                        raiseHandButton.setDisable(false);
                        done = false;
                        pauseIndex = 0;
                        incorrectAnswerReceived = false;
                                  questionCount++;
                        if (questionThread != null && questionThread.isAlive()) {
                          questionThread.interrupt();
                        }
    
                        //クイズが5問終了した場合、最終スコアが表示
                        if (questionCount > quizlimitation) {
                            questionArea.setText("全てのクイズが終了しました！");
                            
                            System.out.println("finalsocresent");
                            out.println("FINALSCORE_c "+ myusername + " " + score);
    
            
                            //回答入力フィールドが無効化
                            answerField.setDisable(true);
                            end.setDisable(false);
        
                        //5問未満の時
                        } else {
                            fullQuestion = message.substring(11);
                            pauseIndex = 0;
                            handleNewQuestion(fullQuestion);
                        }
    
                    //受信したメッセージが"CORRECT_s  "で始まる場合
                    } else if (message.startsWith("CORRECT_s ")) {
                        
                        //回答入力フィールドが無効化
                        answerField.setDisable(true);
        
                        //スコアが更新
                        ///"CORRECT_s"以降(10文字目から)の得点をscoreとする
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
                        
                        //回答入力フィールドが無効化
                        answerField.setDisable(true);
        
                    //受信したメッセージが"WRONG_s "で始まる場合
                    }else if(message.startsWith("WRONG_s ")){
                      incorrectAnswerReceived = true;
                      pauseIndex = questionArea.getText().length();
                      startQuestionThread();

                    //トータルスコアの提示
                    }else if (message.startsWith("PAUSE_s")) {
                        raiseHandButton.setDisable(true);
                        paused = true;
                          new Thread(
                           () -> {
                             try {
                               Thread.sleep(6000);
                               paused = false;
                               startQuestionThread();
                               if (!done) {
                                 Platform.runLater(
                                  () -> raiseHandButton.setDisable(false)
                                );
                              } else {
                                Platform.runLater(() -> answerField.setDisable(true));
                              }
                            } catch (InterruptedException e) {
                               e.printStackTrace();
                             }
                            }           
                            ).start();
                    }else if(message.startsWith("TOTALSCORE_s ")){
                      System.out.println("TOTALSCORE called");
                    
                        //totalscore = Integer.parseInt(message.substring(13));
                        //今の問題を補完して
                        String currentText = questionArea.getText();
                        //間違いのメッセージを追加して表示
                        String appendedText = currentText +"\n"+ message.substring(13);
                        questionArea.setText(appendedText);
                    }else if(message.startsWith("CHAT_s")){
                        String chatMessage = message.substring(7);
                        updateChatArea(chatMessage);
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
        //サーバーに"START"メッセージを送信
        out.println("START_c");
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

  private void handleNewQuestion(String question) {
    if (questionThread != null && questionThread.isAlive()) {
      questionThread.interrupt();
    }

    fullQuestion = question;
    startQuestionThread();
  }

  private void startQuestionThread() {
    if (questionCount > quizlimitation) {
      return; // If questionCount exceeds 5, don't start a new thread.
    }

    if (questionThread != null && questionThread.isAlive()) {
      questionThread.interrupt();

      try {
        questionThread.join(); // wait for the previous thread to finish
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    questionThread =
      new Thread(
        () -> {
          try {
            Thread.sleep(300); // Give the thread some time to fully start before enabling the "raise hand" button.

            for (int i = pauseIndex; i < fullQuestion.length(); i++) {
              if (
                Thread.interrupted() ||
                questionCount > quizlimitation
              ) {
                incorrectAnswerReceived = false;
                break;
              }

              final String questionToDisplay = fullQuestion.substring(0, i + 1);

              Platform.runLater(() -> questionArea.setText(questionToDisplay));

              if (paused) {
                pauseIndex = i; // update pauseIndex here
                while (paused) {
                  Thread.sleep(50); // Check pause state every 50 milliseconds
                }
              } else {
                Thread.sleep(50); // Delay for 50 milliseconds if not paused
              }
            }
            pauseIndex = fullQuestion.length(); // update pauseIndex to the end of the question
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      );

    synchronized (questionThread) {
      questionThread.start();
    }
  }
}