import java.io.*;
import java.net.*;
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

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
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

        // ルートレイアウトの作成
        VBox rootLayout = new VBox(20);
        rootLayout.getChildren().addAll(loginLayout, registrationLayout);
        rootLayout.setAlignment(Pos.CENTER);

        // シーンの作成
        Scene scene = new Scene(rootLayout, 400, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        connectToServer();

        new Thread(() -> {
            try {
                String message;
                while ((message = in.readLine()) != null) {

                    // ログイン成功時
                    if (message.startsWith("SUCCESS_s")) {
                        Platform.runLater(() -> {
                            messageLabel.setText("Login successful");
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
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

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