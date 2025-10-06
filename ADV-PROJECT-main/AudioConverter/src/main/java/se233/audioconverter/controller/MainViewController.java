package se233.audioconverter.controller;

import se233.audioconverter.Launcher;
import se233.audioconverter.exception.AudioConversionException;
import se233.audioconverter.model.AudioFile;
import se233.audioconverter.model.ConversionSettings;
import se233.audioconverter.model.ConversionPreset;
import se233.audioconverter.service.FFmpegService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.geometry.Pos;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

public class MainViewController {
    private static final List<String> SUPPORTED_FORMATS = Arrays.asList("mp3", "wav", "m4a", "flac");

    // Stage 1: File Drop
    @FXML private StackPane mainStackPane;
    @FXML private VBox fileDropStage;
    @FXML private VBox dropZone;
    @FXML private VBox filePreviewBox;
    @FXML private ListView<AudioFile> filePreviewList;
    @FXML private Label fileCountLabel;
    @FXML private Button nextButton;

    // Stage 2: Configuration
    @FXML private VBox configStage;
    @FXML private Label configFileCountLabel;
    @FXML private ListView<AudioFile> fileListView;
    @FXML private ComboBox<ConversionSettings.OutputFormat> formatComboBox;
    @FXML private Label formatInfoLabel;

    // Preset Management
    @FXML private ComboBox<ConversionPreset> presetComboBox;
    @FXML private Button loadPresetButton;
    @FXML private Label presetDescriptionLabel;

    // Bitrate Settings
    @FXML private VBox bitrateSettingsBox;
    @FXML private Slider qualitySlider;
    @FXML private Label qualityLabel;
    @FXML private ComboBox<Integer> bitrateComboBox;

    // WAV Quality Settings (Sample Rate based)
    @FXML private VBox wavQualityBox;
    @FXML private Slider wavQualitySlider;
    @FXML private Label wavQualityLabel;

    // Advanced Settings
    @FXML private VBox advancedSettingsBox;
    @FXML private CheckBox showAdvancedCheckBox;
    @FXML private ComboBox<ConversionSettings.SampleRate> sampleRateComboBox;
    @FXML private ComboBox<ConversionSettings.Channels> channelsComboBox;

    // Bitrate Mode (in Advanced Settings, MP3 and M4A)
    @FXML private VBox bitrateModeBox;
    @FXML private Label bitrateModeLabel;
    @FXML private HBox bitrateModeRadioBox;
    @FXML private ToggleGroup bitrateToggleGroup;
    @FXML private RadioButton constantBitrateRadio;
    @FXML private RadioButton variableBitrateRadio;
    @FXML private VBox cbrBitrateBox;
    @FXML private VBox vbrQualityBox;
    @FXML private Slider vbrQualitySlider;
    @FXML private Label vbrQualityLabel;

    @FXML private Button convertButton;
    @FXML private Button clearButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;

    private ObservableList<AudioFile> audioFiles;
    private ConversionSettings settings;
    private FFmpegService ffmpegService;
    private ExecutorService executorService;

    @FXML
    public void initialize() {
        audioFiles = FXCollections.observableArrayList();
        settings = new ConversionSettings();

        try {
            ffmpegService = new FFmpegService();
        } catch (IOException e) {
            showError("FFmpeg Initialization Error",
                    "Could not initialize FFmpeg. Make sure FFmpeg is installed and in your PATH.\n\n" +
                            "Error: " + e.getMessage());
            return;
        }

        executorService = Executors.newFixedThreadPool(4);

        setupStage1();
        setupStage2();

        showStage1();
    }

    private void setupStage1() {
        filePreviewList.setItems(audioFiles);
        filePreviewList.setCellFactory(param -> new ListCell<AudioFile>() {
            @Override
            protected void updateItem(AudioFile item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // สร้าง HBox สำหรับแสดงชื่อไฟล์และปุ่มลบ
                    HBox hbox = new HBox(10);
                    hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                    // Label แสดงชื่อไฟล์
                    Label fileLabel = new Label(item.getName() + " (" + item.getFormat().toUpperCase() + ")");
                    fileLabel.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(fileLabel, javafx.scene.layout.Priority.ALWAYS);

                    // ปุ่มลบ
                    Button deleteButton = new Button("×");
                    deleteButton.setStyle(
                            "-fx-background-color: #a1c1c4; " +
                                    "-fx-text-fill: white; " +
                                    "-fx-font-size: 16; " +
                                    "-fx-font-weight: bold; " +
                                    "-fx-padding: 2 8; " +
                                    "-fx-cursor: hand;"
                    );
                    deleteButton.setOnAction(e -> {
                        audioFiles.remove(item);
                        updateFilePreview();
                    });

                    hbox.getChildren().addAll(fileLabel, deleteButton);
                    setGraphic(hbox);
                    setText(null);
                }
            }
        });

        setupDragAndDrop(dropZone);
    }

    private void setupStage2() {
        fileListView.setItems(audioFiles);
        fileListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(AudioFile item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.toString());
                    switch (item.getStatus()) {
                        case PENDING -> setStyle("-fx-text-fill: black;");
                        case PROCESSING -> setStyle("-fx-text-fill: blue; -fx-font-weight: bold;");
                        case COMPLETED -> setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                        case FAILED -> setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    }
                }
            }
        });

        formatComboBox.setItems(FXCollections.observableArrayList(
                ConversionSettings.OutputFormat.values()));
        formatComboBox.setValue(ConversionSettings.OutputFormat.MP3);
        formatComboBox.setOnAction(e -> {
            settings.setOutputFormat(formatComboBox.getValue());
            updateFormatUI();
        });

        setupPresets();

        qualitySlider.setMin(0);
        qualitySlider.setMax(3);
        qualitySlider.setValue(2);
        qualitySlider.setMajorTickUnit(1);
        qualitySlider.setMinorTickCount(0);
        qualitySlider.setSnapToTicks(true);
        qualitySlider.setShowTickMarks(true);
        qualitySlider.setShowTickLabels(true);

        qualitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int index = newVal.intValue();
            ConversionSettings.Quality quality = ConversionSettings.Quality.values()[index];
            settings.setQuality(quality);
            qualityLabel.setText(String.format("%s (%d kbps)",
                    quality.getLabel(), quality.getBitrate()));

            if (settings.getOutputFormat().supportsBitrate()) {
                bitrateComboBox.setValue(quality.getBitrate());
            }
        });
        qualityLabel.setText("Good (192 kbps)");

        bitrateComboBox.setOnAction(e -> {
            Integer selectedBitrate = bitrateComboBox.getValue();
            if (selectedBitrate != null) {
                settings.setCustomBitrate(selectedBitrate);
            }
        });

        constantBitrateRadio.setOnAction(e -> {
            settings.setBitrateMode(ConversionSettings.BitrateMode.CONSTANT);
            updateBitrateModeUI();
        });

        variableBitrateRadio.setOnAction(e -> {
            settings.setBitrateMode(ConversionSettings.BitrateMode.VARIABLE);
            updateBitrateModeUI();
        });

        if (vbrQualitySlider != null) {
            vbrQualitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                int vbrQuality = newVal.intValue();
                settings.setVbrQuality(vbrQuality);

                String[] vbrLabels = {"Best", "High", "Normal", "Medium", "Low", "Smallest"};
                if (vbrQualityLabel != null) {
                    vbrQualityLabel.setText(vbrQuality + " (" + vbrLabels[vbrQuality] + ")");
                }
            });
            if (vbrQualityLabel != null) {
                vbrQualityLabel.setText("2 (Normal)");
            }
        }

        if (wavQualitySlider != null) {
            wavQualitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                int qualityIndex = newVal.intValue();

                int[] sampleRates = {22050, 44100, 48000, 96000};
                String[] qualityLabels = {"Tape (22050 Hz)", "CD Quality (44100 Hz)",
                        "DVD (48000 Hz)", "Extra High (96000 Hz)"};

                int selectedRate = sampleRates[qualityIndex];
                ConversionSettings.SampleRate sr = ConversionSettings.SampleRate.fromRate(selectedRate);
                settings.setSampleRate(sr);

                if (wavQualityLabel != null) {
                    wavQualityLabel.setText(qualityLabels[qualityIndex]);
                }

                if (sampleRateComboBox != null) {
                    sampleRateComboBox.setValue(sr);
                }
            });
            if (wavQualityLabel != null) {
                wavQualityLabel.setText("DVD (48000 Hz)");
            }
        }

        sampleRateComboBox.setOnAction(e -> {
            settings.setSampleRate(sampleRateComboBox.getValue());
        });

        channelsComboBox.setItems(FXCollections.observableArrayList(
                ConversionSettings.Channels.values()));
        channelsComboBox.setValue(ConversionSettings.Channels.STEREO);
        channelsComboBox.setOnAction(e -> {
            settings.setChannels(channelsComboBox.getValue());
        });

        advancedSettingsBox.setVisible(false);
        advancedSettingsBox.setManaged(false);
        showAdvancedCheckBox.setOnAction(e -> {
            boolean show = showAdvancedCheckBox.isSelected();
            advancedSettingsBox.setVisible(show);
            advancedSettingsBox.setManaged(show);
        });

        convertButton.setOnAction(e -> onConvert());
        clearButton.setOnAction(e -> onClear());

        progressBar.setProgress(0);
        statusLabel.setText("Ready");

        updateFormatUI();
    }

    private void setupPresets() {
        presetComboBox.setItems(FXCollections.observableArrayList(
                ConversionPreset.values()));

        presetComboBox.setOnAction(e -> {
            ConversionPreset selected = presetComboBox.getValue();
            if (selected != null) {
                presetDescriptionLabel.setText(selected.getDescription());
            } else {
                presetDescriptionLabel.setText("");
            }
        });

        loadPresetButton.setOnAction(e -> onLoadPreset());
    }

    @FXML
    private void onLoadPreset() {
        ConversionPreset preset = presetComboBox.getValue();

        if (preset == null) {
            showError("No Preset Selected", "Please select a preset first.");
            return;
        }

        settings.loadFromPreset(preset);

        formatComboBox.setValue(preset.getFormat());
        updateFormatUI();

        channelsComboBox.setValue(preset.getChannels());
        sampleRateComboBox.setValue(preset.getSampleRate());

        // เพิ่มส่วนนี้: อัปเดต Quality Slider ตาม bitrate ของ preset
        if (preset.getFormat().supportsBitrate() &&
                preset.getBitrateMode() == ConversionSettings.BitrateMode.CONSTANT) {

            int presetBitrate = preset.getBitrate();

            // หา Quality index ที่ตรงกับ bitrate
            ConversionSettings.Quality matchedQuality = null;
            for (ConversionSettings.Quality q : ConversionSettings.Quality.values()) {
                if (q.getBitrate() == presetBitrate) {
                    matchedQuality = q;
                    break;
                }
            }

            // อัปเดต slider
            if (matchedQuality != null) {
                qualitySlider.setValue(matchedQuality.ordinal());
                qualityLabel.setText(String.format("%s (%d kbps)",
                        matchedQuality.getLabel(), matchedQuality.getBitrate()));
            }
        }

        if (preset.getFormat().supportsBitrate()) {
            if (preset.getBitrateMode() == ConversionSettings.BitrateMode.CONSTANT) {
                constantBitrateRadio.setSelected(true);
                bitrateComboBox.setValue(preset.getBitrate());
            } else {
                variableBitrateRadio.setSelected(true);
                vbrQualitySlider.setValue(preset.getVbrQuality());
            }
            updateBitrateModeUI();
        }

        statusLabel.setText("Loaded preset: " + preset.getDisplayName());

        showInfo("Preset Loaded",
                "Successfully loaded preset:\n\n" + preset.getDetailedDescription());
    }

    private void setupDragAndDrop(VBox targetZone) {
        targetZone.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                boolean hasValidFile = db.getFiles().stream()
                        .anyMatch(file -> isAudioFile(file.getName()));
                if (hasValidFile) {
                    event.acceptTransferModes(TransferMode.COPY);
                    targetZone.setStyle("-fx-border-color: #2196F3; -fx-border-width: 3; -fx-border-style: dashed; -fx-border-radius: 10; -fx-background-color: #E3F2FD; -fx-background-radius: 10; -fx-padding: 60;");
                }
            }
            event.consume();
        });

        targetZone.setOnDragExited(event -> {
            targetZone.setStyle("-fx-border-color: #4CAF50; -fx-border-width: 3; -fx-border-style: dashed; -fx-border-radius: 10; -fx-background-color: #f9f9f9; -fx-background-radius: 10; -fx-padding: 60;");
            event.consume();
        });

        targetZone.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasFiles()) {
                List<File> validFiles = db.getFiles().stream()
                        .filter(file -> isAudioFile(file.getName()))
                        .toList();

                for (File file : validFiles) {
                    AudioFile audioFile = new AudioFile(file.getAbsolutePath());
                    boolean exists = audioFiles.stream()
                            .anyMatch(af -> af.getFilePath().equals(audioFile.getFilePath()));
                    if (!exists) {
                        audioFiles.add(audioFile);
                    }
                }

                success = !validFiles.isEmpty();
                updateFilePreview();
            }

            targetZone.setStyle("-fx-border-color: #4CAF50; -fx-border-width: 3; -fx-border-style: dashed; -fx-border-radius: 10; -fx-background-color: #f9f9f9; -fx-background-radius: 10; -fx-padding: 60;");
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void updateFormatUI() {
        ConversionSettings.OutputFormat format = settings.getOutputFormat();

        if (format.supportsBitrate()) {
            formatInfoLabel.setText("Lossy compression format");
        } else {
            formatInfoLabel.setText("Lossless format");
        }

        if (format == ConversionSettings.OutputFormat.WAV) {
            bitrateSettingsBox.setVisible(false);
            bitrateSettingsBox.setManaged(false);
            if (wavQualityBox != null) {
                wavQualityBox.setVisible(true);
                wavQualityBox.setManaged(true);
            }
        } else if (format == ConversionSettings.OutputFormat.FLAC) {
            bitrateSettingsBox.setVisible(false);
            bitrateSettingsBox.setManaged(false);
            if (wavQualityBox != null) {
                wavQualityBox.setVisible(false);
                wavQualityBox.setManaged(false);
            }
        } else {
            bitrateSettingsBox.setVisible(true);
            bitrateSettingsBox.setManaged(true);
            if (wavQualityBox != null) {
                wavQualityBox.setVisible(false);
                wavQualityBox.setManaged(false);
            }
        }

        List<Integer> sampleRateOptions = format.getSampleRateOptions();
        if (sampleRateComboBox != null) {
            ObservableList<ConversionSettings.SampleRate> availableRates =
                    FXCollections.observableArrayList();

            for (int rate : sampleRateOptions) {
                availableRates.add(ConversionSettings.SampleRate.fromRate(rate));
            }

            sampleRateComboBox.setItems(availableRates);
            sampleRateComboBox.setValue(ConversionSettings.SampleRate.SR_44100);
            settings.setSampleRate(ConversionSettings.SampleRate.SR_44100);
        }

        boolean showBitrateMode = (format == ConversionSettings.OutputFormat.MP3 ||
                format == ConversionSettings.OutputFormat.M4A);

        if (bitrateModeBox != null) {
            bitrateModeBox.setVisible(showBitrateMode);
            bitrateModeBox.setManaged(showBitrateMode);
        }

        if (showBitrateMode) {
            if (format == ConversionSettings.OutputFormat.MP3) {
                if (bitrateModeLabel != null) {
                    bitrateModeLabel.setText("Bitrate Mode (MP3)");
                }
                if (variableBitrateRadio != null) {
                    variableBitrateRadio.setVisible(true);
                    variableBitrateRadio.setManaged(true);
                }
            } else if (format == ConversionSettings.OutputFormat.M4A) {
                if (bitrateModeLabel != null) {
                    bitrateModeLabel.setText("Bitrate Mode (M4A)");
                }
                if (variableBitrateRadio != null) {
                    variableBitrateRadio.setVisible(false);
                    variableBitrateRadio.setManaged(false);
                }
                if (constantBitrateRadio != null) {
                    constantBitrateRadio.setSelected(true);
                }
                settings.setBitrateMode(ConversionSettings.BitrateMode.CONSTANT);
            }

            List<Integer> bitrateOptions = format.getBitrateOptions();
            if (bitrateComboBox != null) {
                bitrateComboBox.setItems(FXCollections.observableArrayList(bitrateOptions));
                bitrateComboBox.setValue(format.getDefaultBitrate());
                settings.setCustomBitrate(format.getDefaultBitrate());
            }
        }

        updateBitrateModeUI();
    }

    private void updateBitrateModeUI() {
        boolean isVBR = variableBitrateRadio != null && variableBitrateRadio.isSelected();
        ConversionSettings.OutputFormat format = settings.getOutputFormat();
        boolean isMp3 = format == ConversionSettings.OutputFormat.MP3;
        boolean isM4a = format == ConversionSettings.OutputFormat.M4A;

        if (isMp3 || isM4a) {
            if (isVBR && isMp3) {
                if (cbrBitrateBox != null) {
                    cbrBitrateBox.setVisible(false);
                    cbrBitrateBox.setManaged(false);
                }
                if (vbrQualityBox != null) {
                    vbrQualityBox.setVisible(true);
                    vbrQualityBox.setManaged(true);
                }
            } else {
                if (cbrBitrateBox != null) {
                    cbrBitrateBox.setVisible(true);
                    cbrBitrateBox.setManaged(true);
                }
                if (vbrQualityBox != null) {
                    vbrQualityBox.setVisible(false);
                    vbrQualityBox.setManaged(false);
                }
            }
        }
    }

    private void updateFilePreview() {
        if (audioFiles.isEmpty()) {
            filePreviewBox.setVisible(false);
            filePreviewBox.setManaged(false);
            nextButton.setDisable(true);
            fileCountLabel.setText("0 files");
        } else {
            filePreviewBox.setVisible(true);
            filePreviewBox.setManaged(true);
            nextButton.setDisable(false);
            fileCountLabel.setText(audioFiles.size() + " file(s)");
        }
        filePreviewList.refresh();
    }

    private boolean isAudioFile(String filename) {
        String extension = "";
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            extension = filename.substring(lastDot + 1).toLowerCase();
        }
        return SUPPORTED_FORMATS.contains(extension);
    }

    private void showStage1() {
        fileDropStage.setVisible(true);
        fileDropStage.setManaged(true);
        configStage.setVisible(false);
        configStage.setManaged(false);
    }

    private void showStage2() {
        fileDropStage.setVisible(false);
        fileDropStage.setManaged(false);
        configStage.setVisible(true);
        configStage.setManaged(true);

        configFileCountLabel.setText(audioFiles.size() + " file(s) selected");
    }

    @FXML
    private void onBrowseFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Audio Files");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.m4a", "*.flac"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(Launcher.primaryStage);

        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            for (File file : selectedFiles) {
                if (isAudioFile(file.getName())) {
                    AudioFile audioFile = new AudioFile(file.getAbsolutePath());
                    boolean exists = audioFiles.stream()
                            .anyMatch(af -> af.getFilePath().equals(audioFile.getFilePath()));
                    if (!exists) {
                        audioFiles.add(audioFile);
                    }
                }
            }
            updateFilePreview();
        }
    }

    @FXML
    private void onNextToConfig() {
        if (audioFiles.isEmpty()) {
            showError("No Files", "Please add audio files first.");
            return;
        }
        showStage2();
    }

    @FXML
    private void onBackToFiles() {
        showStage1();
    }

    @FXML
    private void onClearFromStage1() {
        audioFiles.clear();
        updateFilePreview();
    }

    @FXML
    private void onConvert() {
        if (audioFiles.isEmpty()) {
            showError("No Files", "Please add audio files to convert.");
            return;
        }

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Output Directory");
        File outputDir = directoryChooser.showDialog(Launcher.primaryStage);

        if (outputDir == null) {
            return;
        }

        setUIDisabled(true);

        audioFiles.forEach(file -> file.setStatus(AudioFile.ConversionStatus.PENDING));
        fileListView.refresh();

        List<AudioConversionTask> tasks = new ArrayList<>();
        for (AudioFile audioFile : audioFiles) {
            AudioConversionTask task = new AudioConversionTask(
                    audioFile, settings, outputDir.getAbsolutePath(), ffmpegService);

            task.setProgressCallback(new AudioConversionTask.ProgressCallback() {
                @Override
                public void onProgress(double percentage, String message) {
                }

                @Override
                public void onStatusChange(AudioFile.ConversionStatus status) {
                    Platform.runLater(() -> fileListView.refresh());
                }
            });

            tasks.add(task);
        }

        Task<Void> masterTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                CompletionService<Void> completionService =
                        new ExecutorCompletionService<>(executorService);

                int totalTasks = tasks.size();
                int completedTasks = 0;

                for (Callable<Void> task : tasks) {
                    completionService.submit(task);
                }

                for (int i = 0; i < totalTasks; i++) {
                    try {
                        Future<Void> future = completionService.take();
                        future.get();
                        completedTasks++;

                        double progress = (double) completedTasks / totalTasks;
                        updateProgress(progress, 1.0);
                        updateMessage(String.format("Completed %d of %d files",
                                completedTasks, totalTasks));

                    } catch (ExecutionException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof AudioConversionException) {
                            AudioConversionException ace = (AudioConversionException) cause;
                            final String errorMsg = ace.getUserFriendlyMessage();
                            Platform.runLater(() ->
                                    showError("Conversion Error", errorMsg));
                        } else {
                            final String errorMsg = "An unexpected error occurred: " +
                                    (cause != null ? cause.getMessage() : "Unknown error");
                            Platform.runLater(() ->
                                    showError("Unexpected Error", errorMsg));
                        }
                    }
                }

                return null;
            }
        };

        progressBar.progressProperty().bind(masterTask.progressProperty());
        statusLabel.textProperty().bind(masterTask.messageProperty());

        masterTask.setOnSucceeded(e -> {
            long successful = audioFiles.stream()
                    .filter(f -> f.getStatus() == AudioFile.ConversionStatus.COMPLETED)
                    .count();
            long failed = audioFiles.stream()
                    .filter(f -> f.getStatus() == AudioFile.ConversionStatus.FAILED)
                    .count();

            statusLabel.textProperty().unbind();
            statusLabel.setText(String.format("Conversion complete: %d successful, %d failed",
                    successful, failed));

            showInfo("Conversion Complete",
                    String.format("Successfully converted %d file(s).\nFailed: %d\n\nOutput location: %s",
                            successful, failed, outputDir.getAbsolutePath()));

            setUIDisabled(false);
        });

        masterTask.setOnFailed(e -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText("Conversion failed");
            showError("Error", "An error occurred during conversion.");
            setUIDisabled(false);
        });

        Thread thread = new Thread(masterTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void setUIDisabled(boolean disabled) {
        convertButton.setDisable(disabled);
        clearButton.setDisable(disabled);
        formatComboBox.setDisable(disabled);
        qualitySlider.setDisable(disabled);
        bitrateComboBox.setDisable(disabled);
        constantBitrateRadio.setDisable(disabled);
        variableBitrateRadio.setDisable(disabled);
        sampleRateComboBox.setDisable(disabled);
        channelsComboBox.setDisable(disabled);
        showAdvancedCheckBox.setDisable(disabled);
        presetComboBox.setDisable(disabled);
        loadPresetButton.setDisable(disabled);

        if (!disabled) {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0);
        }
    }

    @FXML
    private void onClear() {
        audioFiles.clear();
        updateFilePreview();
        fileListView.refresh();
        statusLabel.setText("Ready");
        progressBar.setProgress(0);

        showStage1();
    }

    @FXML
    private void onClose() {
        if (executorService != null) {
            executorService.shutdown();
        }
        Platform.exit();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
