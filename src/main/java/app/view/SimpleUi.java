package app.view;

import app.model.AIPreprocessor;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class SimpleUi extends Application {

    private TableView<ReportEntry> table;
    private ObservableList<ReportEntry> tableData;
    private ListView<String> excelList;
    private ObservableList<String> excelData;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Label fileCountLabel;
    private AIPreprocessor aiPreprocessor;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Vision2XLS");

        aiPreprocessor = new AIPreprocessor();

        // --- SIDEBAR ---
        VBox sidebar = new VBox(25);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(300);

        // Логотип
        Label logoLabel = new Label("Vision2XLS");
        logoLabel.getStyleClass().add("logo-text");

        HBox badgeBox = new HBox();
        Label versionBadge = new Label("v2.2.8");
        versionBadge.getStyleClass().add("badge");
        badgeBox.getChildren().add(versionBadge);

        VBox logoBox = new VBox(5, logoLabel, badgeBox);
        logoBox.setPadding(new Insets(0, 0, 20, 0));

        // Секция 1: Изображения
        Label sourceHeader = new Label("Изображения");
        sourceHeader.getStyleClass().add("section-header");

        Button selectFilesBtn = createStyledButton("Загрузить фото", "📷");
        fileCountLabel = new Label("Файлы не выбраны");
        fileCountLabel.getStyleClass().add("status-text-subtle");

        // Секция 2: Excel
        Label excelHeader = new Label("Реестр (Excel)");
        excelHeader.getStyleClass().add("section-header");

        Button selectExcelBtn = createStyledButton("Загрузить Excel", "📊");

        excelList = new ListView<>();
        excelData = FXCollections.observableArrayList();
        excelList.setItems(excelData);
        excelList.getStyleClass().add("modern-list");
        excelList.setPlaceholder(new Label("Список пуст"));
        VBox.setVgrow(excelList, Priority.ALWAYS);

        // Кнопка Process
        Button processBtn = new Button("Начать обработку");
        processBtn.getStyleClass().add("btn-primary");
        processBtn.setMaxWidth(Double.MAX_VALUE);
        processBtn.setPrefHeight(50);

        // Сборка сайдбара
        sidebar.getChildren().addAll(
                logoBox,
                sourceHeader, selectFilesBtn, fileCountLabel,
                new Region() {{
                    setMinHeight(10);
                }},
                excelHeader, selectExcelBtn, excelList,
                new Region() {{
                    setMinHeight(10);
                }},
                processBtn
        );

        // --- MAIN AREA ---
        VBox mainArea = new VBox(25);
        mainArea.getStyleClass().add("main-area");

        // Header Main Area
        HBox headerBox = new HBox(20);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label("Очередь обработки");
        title.getStyleClass().add("h2");
        Label subtitle = new Label("Отслеживайте статус загруженных документов в реальном времени");
        subtitle.getStyleClass().add("subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Status Box
        HBox statusBox = new HBox(10);
        statusBox.setAlignment(Pos.CENTER_RIGHT);
        statusLabel = new Label("Система готова");
        statusLabel.getStyleClass().add("status-badge-ready");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(120);
        progressBar.setVisible(false);

        statusBox.getChildren().addAll(progressBar, statusLabel);
        headerBox.getChildren().addAll(titleBox, spacer, statusBox);

        // TABLE CONTAINER (The Card)
        VBox tableContainer = new VBox();
        tableContainer.getStyleClass().add("card");
        VBox.setVgrow(tableContainer, Priority.ALWAYS);

        table = new TableView<>();
        tableData = FXCollections.observableArrayList();
        table.setItems(tableData);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getStyleClass().add("modern-table");

        // Custom Placeholder (когда таблица пустая)
        VBox placeholder = new VBox(15);
        placeholder.setAlignment(Pos.CENTER);
        Label iconPlace = new Label("📂");
        iconPlace.setStyle("-fx-font-size: 40px; -fx-text-fill: #cbd5e1;");
        Label textPlace = new Label("Нет данных для отображения");
        textPlace.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        Label subPlace = new Label("Загрузите файлы через меню слева");
        subPlace.setStyle("-fx-font-size: 13px; -fx-text-fill: #cbd5e1;");
        placeholder.getChildren().addAll(iconPlace, textPlace, subPlace);
        table.setPlaceholder(placeholder);

        createColumn("Название файла", "fileName", 0.45);
        createColumn("Время", "uploadTime", 0.25);
        createColumn("Статус", "status", 0.30); // Статус переделаем в CellFactory для цвета позже

        tableContainer.getChildren().add(table);
        mainArea.getChildren().addAll(headerBox, tableContainer);

        // --- ROOT ---
        BorderPane root = new BorderPane();
        root.setLeft(sidebar);
        root.setCenter(mainArea);

        Scene scene = new Scene(root, 1150, 750); // Чуть больше места

        // --- HANDLERS (Minimal) ---
        selectFilesBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            List<File> f = fc.showOpenMultipleDialog(primaryStage);
            if (f != null) {
                fileCountLabel.setText("Выбрано: " + f.size() + " файл(ов)");
                fileCountLabel.getStyleClass().add("text-accent");
            }
        });

        Thread initThread = new Thread(() -> {
            try {
                aiPreprocessor.init();
                System.out.println("start");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        initThread.setDaemon(true);
        initThread.start();

        String cssData = "data:text/css;base64," + Base64.getEncoder().encodeToString(MODERN_CSS.getBytes(StandardCharsets.UTF_8));
        scene.getStylesheets().add(cssData);

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private Button createStyledButton(String text, String icon) {
        Button btn = new Button(text);
        btn.setGraphic(new Label(icon));
        btn.getStyleClass().add("btn-secondary");
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    private void createColumn(String title, String property, Double widthPercent) {
        TableColumn<ReportEntry, String> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        col.setMaxWidth(1f * Integer.MAX_VALUE * widthPercent);
        table.getColumns().add(col);
    }

    // --- Model ---
    public static class ReportEntry {
        private final SimpleStringProperty fileName;
        private final SimpleStringProperty uploadTime;
        private final SimpleStringProperty status;

        public ReportEntry(String name, String time, String st) {
            this.fileName = new SimpleStringProperty(name);
            this.uploadTime = new SimpleStringProperty(time);
            this.status = new SimpleStringProperty(st);
        }

        public String getFileName() {
            return fileName.get();
        }

        public String getUploadTime() {
            return uploadTime.get();
        }

        public String getStatus() {
            return status.get();
        }
    }

    // --- CSS: The RTX 5090 Edition ---
    private static final String MODERN_CSS = """
            /* FONTS & BASICS */
            .root {
                -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;
                -fx-base: #f8fafc;
                -fx-background-color: #f8fafc; /* Slate-50 */
                -fx-focus-color: transparent;
                -fx-faint-focus-color: transparent;
            }
            
            /* SIDEBAR */
            .sidebar {
                -fx-background-color: #ffffff;
                -fx-padding: 30;
                -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 15, 0, 0, 0);
            }
            .logo-text {
                -fx-font-size: 24px;
                -fx-font-weight: 900;
                -fx-text-fill: #0f172a;
            }
            .badge {
                -fx-background-color: #e0f2fe;
                -fx-text-fill: #0284c7;
                -fx-font-size: 10px;
                -fx-font-weight: bold;
                -fx-padding: 2 8;
                -fx-background-radius: 10;
            }
            .section-header {
                -fx-font-size: 11px;
                -fx-font-weight: 700;
                -fx-text-fill: #94a3b8; /* Slate-400 */
                -fx-padding: 0 0 5 0;
                -fx-text-transform: uppercase;
            }
            
            /* BUTTONS */
            .btn-secondary {
                -fx-background-color: #ffffff;
                -fx-border-color: #e2e8f0;
                -fx-border-radius: 8;
                -fx-text-fill: #334155;
                -fx-font-size: 13px;
                -fx-font-weight: 600;
                -fx-padding: 10 15;
                -fx-alignment: CENTER_LEFT;
                -fx-cursor: hand;
                -fx-graphic-text-gap: 10;
            }
            .btn-secondary:hover {
                -fx-background-color: #f1f5f9;
                -fx-border-color: #cbd5e1;
            }
            
            .btn-primary {
                /* Gradient Background */
                -fx-background-color: linear-gradient(to right, #2563eb, #3b82f6);
                -fx-text-fill: white;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-background-radius: 8;
                -fx-cursor: hand;
                -fx-effect: dropshadow(three-pass-box, rgba(37, 99, 235, 0.3), 10, 0, 0, 4);
            }
            .btn-primary:hover {
                -fx-background-color: linear-gradient(to right, #1d4ed8, #2563eb);
                -fx-scale-y: 1.02; /* Subtle pop */
            }
            
            /* MAIN AREA & CARD */
            .main-area {
                -fx-padding: 40 50;
            }
            .h2 {
                -fx-font-size: 26px;
                -fx-font-weight: 800;
                -fx-text-fill: #1e293b;
            }
            .subtitle {
                -fx-font-size: 14px;
                -fx-text-fill: #64748b;
            }
            .card {
                -fx-background-color: white;
                -fx-background-radius: 12;
                -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.06), 20, 0, 0, 5);
                -fx-padding: 5; /* Border wrapper */
            }
            
            /* LABELS */
            .status-text-subtle {
                -fx-font-size: 11px;
                -fx-text-fill: #94a3b8;
            }
            .text-accent {
                -fx-text-fill: #2563eb;
                -fx-font-weight: bold;
            }
            .status-badge-ready {
                -fx-background-color: #dcfce7;
                -fx-text-fill: #166534;
                -fx-padding: 5 12;
                -fx-background-radius: 15;
                -fx-font-size: 12px;
                -fx-font-weight: bold;
            }
            
            /* TABLE VIEW - THE CLEAN LOOK */
            .table-view {
                -fx-background-color: white;
                -fx-background-radius: 8;
                -fx-border-width: 0;
            }
            /* Removes the ugly default header gray background */
            .table-view .column-header-background {
                -fx-background-color: transparent;
                -fx-border-width: 0 0 1 0;
                -fx-border-color: #e2e8f0;
            }
            .table-view .column-header {
                -fx-background-color: transparent;
                -fx-size: 45px;
                -fx-padding: 0 10;
            }
            .table-view .column-header .label {
                -fx-text-fill: #64748b;
                -fx-font-weight: 700;
                -fx-font-size: 11px;
                -fx-text-transform: uppercase;
            }
            
            /* Rows */
            .table-row-cell {
                -fx-background-color: white;
                -fx-border-width: 0 0 1 0;
                -fx-border-color: #f1f5f9;
                -fx-padding: 8 0;
            }
            .table-row-cell:hover {
                -fx-background-color: #f8fafc;
            }
            .table-row-cell:selected {
                -fx-background-color: #eff6ff; /* Light Blue */
            }
            .table-row-cell:selected .text {
                -fx-fill: #1e293b;
            }
            
            /* Scrollbars */
            .scroll-bar:vertical {
                -fx-background-color: transparent;
            }
            .scroll-bar .thumb {
                -fx-background-color: #cbd5e1;
                -fx-background-radius: 4;
            }
            
            /* LIST VIEW */
            .list-view {
                -fx-background-color: #f8fafc;
                -fx-background-radius: 8;
                -fx-border-color: transparent;
            }
            .list-cell {
                -fx-background-color: transparent;
                -fx-padding: 10;
                -fx-font-size: 13px;
            }
            .list-cell:filled:selected {
                -fx-background-color: #e2e8f0;
                -fx-text-fill: #0f172a;
                -fx-background-radius: 6;
            }
            """;

    public static void main(String[] args) {
        launch(args);
    }
}