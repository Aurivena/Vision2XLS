package app.view;

import app.controller.Handler;
import app.model.AIClient;
import app.model.AIPreprocessor;
import app.model.ExcelService;
import javafx.application.Application;
import javafx.application.Platform;
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
import javafx.util.Callback;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SimpleUi extends Application {

    private TableView<ReportEntry> table;
    private ObservableList<ReportEntry> tableData;
    private ListView<CheckBox> excelList;

    private Label statusLabel;
    private ProgressBar progressBar;
    private Label fileCountLabel;
    private AIPreprocessor aiPreprocessor;

    private Handler handler;

    // Состояние
    private Map<Integer, String> selectedColumns;
    private Map<Integer, String> activeColumnSet = new HashMap<>();
    private List<File> selectedImageFiles = new ArrayList<>();
    private File selectedExcelFile = null;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Vision2XLS");
        handler = new Handler();
        aiPreprocessor = new AIPreprocessor();

        // --- SIDEBAR ---
        VBox sidebar = new VBox(25);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(300);

        // Логотип
        Label logoLabel = new Label("Vision2XLS");
        logoLabel.getStyleClass().add("logo-text");

        HBox badgeBox = new HBox();
        Label versionBadge = new Label("v2.3.0");
        versionBadge.getStyleClass().add("badge");
        badgeBox.getChildren().add(versionBadge);

        VBox logoBox = new VBox(5, logoLabel, badgeBox);
        logoBox.setPadding(new Insets(0, 0, 20, 0));

        // Секция 1: Изображения
        Label sourceHeader = new Label("Изображения");
        sourceHeader.getStyleClass().add("section-header");

        Button selectFilesBtn = createStyledButton("Добавить фото", "📷");

        Button clearFilesBtn = createStyledButton("Очистить список", "🗑");
        clearFilesBtn.setOnAction(e -> {
            selectedImageFiles.clear();
            tableData.clear();
            updateFileCount();
        });

        fileCountLabel = new Label("Файлы не выбраны");
        fileCountLabel.getStyleClass().add("status-text-subtle");

        // Секция 2: Excel
        Label excelHeader = new Label("Реестр (Excel)");
        excelHeader.getStyleClass().add("section-header");

        Button selectExcelBtn = createStyledButton("Загрузить Excel", "📊");

        excelList = new ListView<>();
        excelList.getStyleClass().add("modern-list");
        excelList.setPlaceholder(new Label("Список пуст"));
        VBox.setVgrow(excelList, Priority.ALWAYS);

        // Кнопка Process
        Button processBtn = new Button("Начать обработку");
        processBtn.getStyleClass().add("btn-primary");
        processBtn.setMaxWidth(Double.MAX_VALUE);
        processBtn.setPrefHeight(50);

        // ВАЖНО: Я убрал отсюда handler.start(...), который был ошибкой

        // Сборка сайдбара
        sidebar.getChildren().addAll(
                logoBox,
                sourceHeader, selectFilesBtn, clearFilesBtn, fileCountLabel,
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

        // Header
        HBox headerBox = new HBox(20);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label("Очередь обработки");
        title.getStyleClass().add("h2");
        Label subtitle = new Label("Управляйте списком файлов и выбирайте колонки");
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

        // Table
        VBox tableContainer = new VBox();
        tableContainer.getStyleClass().add("card");
        VBox.setVgrow(tableContainer, Priority.ALWAYS);

        table = new TableView<>();
        tableData = FXCollections.observableArrayList();
        table.setItems(tableData);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getStyleClass().add("modern-table");

        VBox placeholder = new VBox(15);
        placeholder.setAlignment(Pos.CENTER);
        Label iconPlace = new Label("📂");
        iconPlace.setStyle("-fx-font-size: 40px; -fx-text-fill: #cbd5e1;");
        Label textPlace = new Label("Нет данных");
        textPlace.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        placeholder.getChildren().addAll(iconPlace, textPlace);
        table.setPlaceholder(placeholder);

        createColumn("Название файла", "fileName", 0.40);
        createColumn("Время", "uploadTime", 0.20);
        addDeleteColumn();

        tableContainer.getChildren().add(table);
        mainArea.getChildren().addAll(headerBox, tableContainer);

        // --- ROOT ---
        BorderPane root = new BorderPane();
        root.setLeft(sidebar);
        root.setCenter(mainArea);

        Scene scene = new Scene(root, 1150, 750);

        // --- HANDLERS ---

        // 1. Загрузка фото
        selectFilesBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Выберите файл pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.png", "*.jpeg","*.pdf"));

            List<File> files = fc.showOpenMultipleDialog(primaryStage);
            if (files != null) {
                this.selectedImageFiles.addAll(files);
                updateFileCount();
                String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                for (File file : files) {
                    tableData.add(new ReportEntry(file, time, "Ожидание"));
                }
            }
        });

        // 2. Загрузка Excel
        selectExcelBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Выберите реестр Excel");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"));

            File file = fc.showOpenDialog(primaryStage);
            if (file != null) {
                this.selectedExcelFile = file;
                excelList.getItems().clear();
                activeColumnSet.clear();

                try {
                    List<String> columns = handler.getExcelColumns(file);
                    if (!columns.isEmpty()) {
                        for (int i = 0; i < columns.size(); i++) {
                            String colName = columns.get(i);
                            if (colName == null || colName.trim().isEmpty()) continue;

                            CheckBox cb = new CheckBox(colName);
                            cb.getStyleClass().add("column-checkbox");
                            int curI = i;
                            cb.setOnAction(event -> {
                                if (cb.isSelected()) {
                                    activeColumnSet.put(curI, colName);
                                } else {
                                    activeColumnSet.remove(curI + 1); // Исправлено: удаляем по ключу (Integer), а не значению
                                }
                            });
                            excelList.getItems().add(cb);
                        }
                    }
                } catch (IOException ex) {
                    showError("Ошибка чтения Excel: " + ex.getMessage());
                }
            }
        });

        // 3. ОБРАБОТКА (START)
        processBtn.setOnAction(e -> {
            // Проверки
            if (selectedImageFiles.isEmpty()) {
                showWarning("Пожалуйста, добавьте изображения.");
                return;
            }
            if (selectedExcelFile == null) {
                showWarning("Пожалуйста, выберите файл Excel.");
                return;
            }
            if (activeColumnSet.isEmpty()) {
                showWarning("Пожалуйста, выберите хотя бы одну колонку.");
                return;
            }

            // Фиксация колонок
            this.selectedColumns = new HashMap<>(activeColumnSet);

            System.out.println("Запуск обработки...");
            System.out.println("Картинок: " + selectedImageFiles.size());
            System.out.println("Выбранные колонки: " + selectedColumns.values());

            // --- UI: ВКЛЮЧАЕМ РЕЖИМ ЗАГРУЗКИ ---
            statusLabel.setText("В работе...");
            statusLabel.setStyle("-fx-text-fill: #ca8a04; -fx-background-color: #fef9c3;");
            progressBar.setVisible(true);
            progressBar.setProgress(-1);
            processBtn.setDisable(true); // Блокируем кнопку

            // --- АСИНХРОННЫЙ ЗАПУСК ---
            CompletableFuture.runAsync(() -> {
                try {
                    // Выполняем тяжелую работу в фоновом потоке
                    handler.start(selectedExcelFile, selectedImageFiles, selectedColumns);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }).thenRun(() -> {
                // --- УСПЕХ (возврат в UI поток) ---
                Platform.runLater(() -> {
                    statusLabel.setText("Готово!");
                    statusLabel.setStyle("-fx-text-fill: #166534; -fx-background-color: #dcfce7;");
                    progressBar.setVisible(false);
                    processBtn.setDisable(false);
                    showInfo("Обработка завершена!");
                });
            }).exceptionally(ex -> {
                // --- ОШИБКА (возврат в UI поток) ---
                Platform.runLater(() -> {
                    statusLabel.setText("Ошибка!");
                    statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-background-color: #fee2e2;");
                    progressBar.setVisible(false);
                    processBtn.setDisable(false);
                    showError("Сбой: " + ex.getCause().getMessage());
                });
                return null;
            });
        });

        // Фоновая инициализация LLM (чтобы UI открылся быстро)
        Thread initThread = new Thread(() -> {
            try {
                aiPreprocessor.init();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        initThread.setDaemon(true);
        initThread.start();

        // Подключение стилей
        String cssData = "data:text/css;base64," + Base64.getEncoder().encodeToString(MODERN_CSS.getBytes(StandardCharsets.UTF_8));
        scene.getStylesheets().add(cssData);

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // --- Helpers ---

    private void updateFileCount() {
        fileCountLabel.setText("Всего: " + selectedImageFiles.size() + " файл(ов)");
        fileCountLabel.getStyleClass().add("text-accent");
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

    private void addDeleteColumn() {
        TableColumn<ReportEntry, Void> col = new TableColumn<>("Удал.");
        col.setMaxWidth(1f * Integer.MAX_VALUE * 0.15);
        Callback<TableColumn<ReportEntry, Void>, TableCell<ReportEntry, Void>> cellFactory = param -> new TableCell<>() {
            private final Button btn = new Button("❌");

            {
                btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-font-weight: bold; -fx-cursor: hand;");
                btn.setOnAction(event -> {
                    ReportEntry entry = getTableView().getItems().get(getIndex());
                    selectedImageFiles.remove(entry.getFile());
                    tableData.remove(entry);
                    updateFileCount();
                });
            }

            @Override
            public void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else setGraphic(btn);
            }
        };
        col.setCellFactory(cellFactory);
        table.getColumns().add(col);
    }

    private void showWarning(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING, msg);
        alert.show();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.show();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg);
        alert.show();
    }

    // --- Model ---
    public static class ReportEntry {
        private final File file;
        private final SimpleStringProperty fileName;
        private final SimpleStringProperty uploadTime;
        private final SimpleStringProperty status;

        public ReportEntry(File file, String time, String st) {
            this.file = file;
            this.fileName = new SimpleStringProperty(file.getName());
            this.uploadTime = new SimpleStringProperty(time);
            this.status = new SimpleStringProperty(st);
        }

        public File getFile() {
            return file;
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

    // --- CSS ---
    private static final String MODERN_CSS = """
            .root { -fx-font-family: 'Segoe UI', 'Roboto', sans-serif; -fx-base: #f8fafc; -fx-background-color: #f8fafc; }
            .sidebar { -fx-background-color: #ffffff; -fx-padding: 30; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 15, 0, 0, 0); }
            .logo-text { -fx-font-size: 24px; -fx-font-weight: 900; -fx-text-fill: #0f172a; }
            .badge { -fx-background-color: #e0f2fe; -fx-text-fill: #0284c7; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 10; }
            .section-header { -fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #94a3b8; -fx-padding: 0 0 5 0; -fx-text-transform: uppercase; }
            .btn-secondary { -fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-text-fill: #334155; -fx-font-size: 13px; -fx-font-weight: 600; -fx-padding: 10 15; -fx-alignment: CENTER_LEFT; -fx-cursor: hand; -fx-graphic-text-gap: 10; }
            .btn-secondary:hover { -fx-background-color: #f1f5f9; -fx-border-color: #cbd5e1; }
            .btn-primary { -fx-background-color: linear-gradient(to right, #2563eb, #3b82f6); -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(37, 99, 235, 0.3), 10, 0, 0, 4); }
            .btn-primary:hover { -fx-background-color: linear-gradient(to right, #1d4ed8, #2563eb); -fx-scale-y: 1.02; }
            .main-area { -fx-padding: 40 50; }
            .h2 { -fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: #1e293b; }
            .subtitle { -fx-font-size: 14px; -fx-text-fill: #64748b; }
            .card { -fx-background-color: white; -fx-background-radius: 12; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.06), 20, 0, 0, 5); -fx-padding: 5; }
            .status-text-subtle { -fx-font-size: 11px; -fx-text-fill: #94a3b8; }
            .text-accent { -fx-text-fill: #2563eb; -fx-font-weight: bold; }
            .status-badge-ready { -fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-padding: 5 12; -fx-background-radius: 15; -fx-font-size: 12px; -fx-font-weight: bold; }
            .table-view { -fx-background-color: white; -fx-background-radius: 8; -fx-border-width: 0; }
            .table-view .column-header-background { -fx-background-color: transparent; -fx-border-width: 0 0 1 0; -fx-border-color: #e2e8f0; }
            .table-view .column-header .label { -fx-text-fill: #64748b; -fx-font-weight: 700; -fx-font-size: 11px; -fx-text-transform: uppercase; }
            .table-row-cell { -fx-background-color: white; -fx-border-width: 0 0 1 0; -fx-border-color: #f1f5f9; -fx-padding: 8 0; }
            .list-view { -fx-background-color: #f8fafc; -fx-background-radius: 8; -fx-border-color: transparent; }
            .list-cell { -fx-background-color: transparent; -fx-padding: 5; }
            .list-cell:filled:selected { -fx-background-color: transparent; }
            .column-checkbox { -fx-font-size: 13px; -fx-text-fill: #334155; -fx-padding: 5; }
            .column-checkbox .box { -fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 3; }
            .column-checkbox:selected .box .mark { -fx-background-color: white; -fx-shape: "M0,4 L2,6 L7,1 L6,0 L2,4 L1,3 z"; }
            .column-checkbox:selected .box { -fx-background-color: #2563eb; -fx-border-color: #2563eb; }
            """;

    public static void main(String[] args) {
        launch(args);
    }
}