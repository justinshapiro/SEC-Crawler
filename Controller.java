package sec_crawler;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.effect.BlendMode;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Pair;

import java.io.*;
import java.net.URL;
import java.util.*;

public class Controller implements Initializable {
    // Crawling thread
    private Thread crawl_thread;

    // Save controls
    private Boolean isSaved = false;
    private File saved_file = null;

    // Store the allowed filing types and their description that we can crawl
    private ArrayList<String> FILING_TYPES = new ArrayList<>();
    private ArrayList<Pair<String, String>> FILING_DESCRIPTIONS = new ArrayList<>();
    private String node1_title, node2_title, node3_title;

    // Preferences
    private Boolean prefs_set = false;
    private TextField prefs1 = null;
    private TextField prefs2 = null;
    private TextField prefs3 = null;

    // Reference UI elements
    @FXML private TextField output_fname_box;
    @FXML private TextField output_flocation_box;
    @FXML private Button browse_button;
    @FXML private ChoiceBox<Integer> year1_box;
    @FXML private ChoiceBox<Integer> year2_box;
    @FXML private Button select_filings_button;
    @FXML private Pane selected_filings_pane;
    @FXML private GridPane selected_filings_gridpane;
    @FXML private ScrollPane wordlist_area;
    @FXML private Button add_wordlist_button;
    @FXML private CheckBox sample_run_checkbox;
    @FXML private CheckBox group_checkbox;
    @FXML private CheckBox exclude_checkbox;
    @FXML private Button run_button;
    @FXML private Button stop_button;
    @FXML public ProgressBar progress_bar;
    @FXML private Label running_label;
    @FXML private MenuBar menu_bar;
    @FXML private MenuItem new_men;
    @FXML private MenuItem open_men;
    @FXML private MenuItem save_men;
    @FXML private MenuItem saveas_men;
    @FXML private MenuItem exit_men;
    @FXML private MenuItem copy_men;
    @FXML private MenuItem paste_men;
    @FXML private MenuItem del_men;
    @FXML private MenuItem prefs_men;

    @Override
    public void initialize(URL u, ResourceBundle rb) {
        initMenuBar();
        initOutputArea();
        initYearArea();
        try {
            loadFilingTypeList();
        } catch (IOException e) { /* do nothing */ }
        initFilingSelectionArea();
        loadWordlistArea();
        initRunButton();
    }

    private void showError(String error_msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(error_msg);
        alert.show();
    }

    private void initRunButton() throws NullPointerException {
        run_button.setOnAction(event -> {
            Boolean error = false;
            // get result file
            if (output_fname_box.getText().equals("")) {
                error = true;
                showError("\"Output File Name\" must have a value.");
            } else if (output_flocation_box.getText().equals("")) {
                error = true;
                showError("\"Output File Location\" must have a value.");
            }

            if (!output_fname_box.getText().contains(".csv")) {
                output_fname_box.setText(output_fname_box.getText() + ".csv");
            }

            String result_file = output_flocation_box.getText() + "\\" + output_fname_box.getText();

            // get years
            if (year1_box.getSelectionModel().isEmpty() || year2_box.getSelectionModel().isEmpty()) {
                error = true;
                showError("You must select both a start and an end year.");
            }

            int year1 = year1_box.getSelectionModel().getSelectedItem();
            int year2 = year2_box.getSelectionModel().getSelectedItem();
            Pair<Integer, Integer> years = new Pair<>(year1, year2);

            // get filing types
            ArrayList<String> filing_types = new ArrayList<>();
            for (Node n : selected_filings_gridpane.getChildren()) {
                if (n != null) {
                    Label l = (Label) n;
                    if (!l.getText().equals("Selected Filings:")) {
                        filing_types.add(l.getText());
                    }
                }
            }

            if (filing_types.isEmpty()) {
                error = true;
                showError("You must select at least one type of filing to crawl.");
            }

            // get wordlists
            ArrayList<WordList> wordlists = getWordlists();

            if (wordlists.isEmpty()) {
                error = true;
                showError("You must add at least one wordlist.");
            }

            // get additional options
            Boolean op1 = false;
            Boolean op2 = false;
            Boolean op3 = false;
            Boolean[] options = new Boolean[3];
            if (sample_run_checkbox.isSelected()) {
                op1 = true;
            }
            if (group_checkbox.isSelected()) {
                op2 = true;
            }
            if (exclude_checkbox.isSelected()) {
                op3 = true;
            }
            options[0] = op1;
            options[1] = op2;
            options[2] = op3;

            // if everything looks good, start the run
            if (!error) {
                lockUI(true);

                crawl_thread = new Thread(new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        SEC_Crawler crawl_agent = new SEC_Crawler(filing_types, years, wordlists, result_file, Controller.this);
                        crawl_agent.setOptions(options);
                        crawl_agent.crawl();

                        Platform.runLater(() -> {
                            lockUI(false);
                        });

                        return null;
                    }
                });

                crawl_thread.setDaemon(true);
                crawl_thread.start();
            }
        });

        stop_button.setOnAction(event -> {
            crawl_thread.stop();
            lockUI(false);
        });
    }

    private void lockUI(Boolean lock) {
        output_fname_box.setDisable(lock);
        output_flocation_box.setDisable(lock);
        browse_button.setDisable(lock);
        year1_box.setDisable(lock);
        year2_box.setDisable(lock);
        select_filings_button.setDisable(lock);
        selected_filings_pane.setDisable(lock);
        selected_filings_gridpane.setDisable(lock);
        wordlist_area.setDisable(lock);
        add_wordlist_button.setDisable(lock);
        sample_run_checkbox.setDisable(lock);
        group_checkbox.setDisable(lock);
        exclude_checkbox.setDisable(lock);
        run_button.setDisable(lock);
        progress_bar.setVisible(lock);
        running_label.setVisible(lock);
        stop_button.setVisible(lock);
        if (!lock) {
            exit_men.setText("Exit / Stop");
        } else {
            exit_men.setText("Exit");
        }
    }

    private ArrayList<WordList> getWordlists() {
        ArrayList<WordList> wordlists = new ArrayList<>();
        ArrayList<Integer> number_check = new ArrayList<>();
        GridPane gp = (GridPane) wordlist_area.getContent();
        for (Node n : gp.getChildren()) {
            if (n != null) {
                if (n.getId().contains("frst_wd_") || n.getId().contains("$")) {
                    int row_num = Integer.parseInt(n.getId().replaceAll("\\D+", ""));
                    if (n.getId().contains("frst_wd_")) {
                        TextField t = (TextField) n;
                        if (number_check.contains(row_num)) {
                            wordlists.get(number_check.indexOf(row_num)).addWord(t.getText());
                        } else {
                            number_check.add(row_num);
                            wordlists.add(new WordList());
                            wordlists.get(number_check.indexOf(row_num)).addWord(t.getText());
                        }
                    } else if (n.getId().contains("$")) {
                        RadioButton r = (RadioButton) n;
                        if (r.isSelected()) {
                            Boolean action_type = false;
                            if (r.getId().contains("sntnce")) {
                                action_type = true;
                            }
                            if (number_check.contains(row_num)) {
                                wordlists.get(number_check.indexOf(row_num)).setActionType(action_type);
                            } else {
                                number_check.add(row_num);
                                wordlists.add(new WordList());
                                wordlists.get(number_check.indexOf(row_num)).setActionType(action_type);
                            }
                        }
                    }
                }
            }
        }

        return wordlists;
    }

    private void initMenuBar() {
        menu_bar.setBlendMode(BlendMode.MULTIPLY);

        new_men.setOnAction(event -> {
            try {
                Stage s = new Stage();
                Parent root = FXMLLoader.load(getClass().getResource("ui.fxml"));
                s.setTitle("SEC Crawler");
                s.setScene(new Scene(root, 690, 816));
                s.setResizable(false);
                s.show();
            } catch (IOException e) { /* do nothing */}
        });

        open_men.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            FileChooser.ExtensionFilter e = new FileChooser.ExtensionFilter("SEC Crawl Files (*.s_crawl)", "*.s_crawl");
            fileChooser.getExtensionFilters().add(e);
            fileChooser.setTitle("Select Crawl Settings File");
            File f = fileChooser.showOpenDialog(new Stage());
            if (f != null) {
                loadSettingsFile(f);
            }
        });

        save_men.setOnAction(event -> {
            if (!isSaved) {
                saved_file = createSettingsFile();
            }
            try {
                writeSettingsFile();
            } catch (IOException e) {  /* do nothing */ }
            isSaved = true;
        });

        saveas_men.setOnAction(event -> {
            saved_file = createSettingsFile();
            try {
                writeSettingsFile();
            } catch (IOException e) {  /* do nothing */ }
            isSaved = true;
        });

        exit_men.setOnAction(event -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Exit Application");
            alert.setHeaderText("Crawling will terminate!");
            alert.setContentText("Choose \"OK\" to end crawling. Otherwise, choose \"Cancel\".");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() != ButtonType.OK) {
                event.consume();
            } else {
                Platform.exit();
            }
        });

        copy_men.setOnAction(event -> {
            Node focusOwner = menu_bar.getScene().getFocusOwner();
            if (focusOwner instanceof TextInputControl) {
                String selectedText = ((TextInputControl)focusOwner).getSelectedText();
                if (! selectedText.isEmpty()) {
                    ClipboardContent clipboardContent = new ClipboardContent() ;
                    clipboardContent.putString(selectedText);
                    Clipboard.getSystemClipboard().setContent(clipboardContent);
                }
            }
        });

        paste_men.setOnAction(event -> {
            Node focusOwner = menu_bar.getScene().getFocusOwner();
            if (focusOwner instanceof TextInputControl) {
                ((TextInputControl)focusOwner).paste();
            }
        });

        del_men.setOnAction(event -> {
            Node focusOwner = menu_bar.getScene().getFocusOwner();
            if (focusOwner instanceof TextInputControl) {
                TextField f = (TextField) focusOwner;
                f.setText("");
            }
        });

        prefs_men.setOnAction(event -> {
            final String prefs1_default = "https://sec.gov/Archives/";
            final String prefs2_default = "5";
            final String prefs3_default = "20";

            Label l1 = new Label("SEC Data Location:");
            Label l2 = new Label("Define sentences to span a maximum of");
            Label l3 = new Label("Define sections to span a maximum of");
            Label l4 = new Label("lines");
            Label l5 = new Label("lines");

            l1.setStyle("-fx-font: 15 calibri;");
            l2.setStyle("-fx-font: 15 calibri;");
            l3.setStyle("-fx-font: 15 calibri;");
            l4.setStyle("-fx-font: 15 calibri;");
            l5.setStyle("-fx-font: 15 calibri;");

            if (!prefs_set) {
                prefs1 = new TextField(prefs1_default);
                prefs2 = new TextField(prefs2_default);
                prefs3 = new TextField(prefs3_default);

                prefs1.setMaxWidth(200);
                prefs2.setMaxWidth(45);
                prefs3.setMaxWidth(45);
            }

            GridPane g1 = new GridPane();
            GridPane g2 = new GridPane();
            GridPane g3 = new GridPane();
            GridPane g4 = new GridPane();

            g1.setHgap(10);
            g2.setHgap(10);
            g3.setHgap(10);
            g4.setHgap(10);
            g4.setVgap(20);

            g1.addRow(0, l1, prefs1);
            g2.addRow(0, l2, prefs2, l4);
            g3.addRow(0, l3, prefs3, l5);
            g4.addRow(0, g1);
            g4.addRow(1, g2);
            g4.addRow(2, g3);

            Pane p = new Pane(g4);

            Stage stage = new Stage();
            stage.setScene(new Scene(p, 400, 120));
            stage.setTitle("Preferences");
            stage.setResizable(false);
            stage.setAlwaysOnTop(true);
            stage.show();

            stage.setOnCloseRequest(event1 -> {
                prefs_set = true;
                if (prefs1.getText().equals("")) {
                    prefs1.setText(prefs1_default);
                }

                if (prefs2.getText().equals("")) {
                    prefs2.setText(prefs2_default);
                }

                if (prefs3.getText().equals("")) {
                    prefs2.setText(prefs3_default);
                }
            });
        });
    }

    private void clearAll() {
        output_fname_box.clear();
        output_flocation_box.clear();
        year1_box.getSelectionModel().clearSelection();
        year2_box.getSelectionModel().clearSelection();
        selected_filings_gridpane.getChildren().clear();
        GridPane g = (GridPane) wordlist_area.getContent();
        g.getChildren().removeAll();
        sample_run_checkbox.setSelected(false);
        group_checkbox.setSelected(false);
        exclude_checkbox.setSelected(false);
    }

    private void loadSettingsFile(File f) {
        clearAll();

        try {
            BufferedReader b = new BufferedReader(new FileReader(f));

            output_fname_box.setText(b.readLine().split(":")[1]);
            output_flocation_box.setText(b.readLine().split("\\|")[1]);

            year1_box.setValue(Integer.parseInt(b.readLine().split(":")[1]));
            year2_box.setValue(Integer.parseInt(b.readLine().split(":")[1]));

            String[] filing_list = b.readLine().split(":")[1].split(",");
            for (String s : filing_list) {
                addFiling(s);
            }

            String next_line = b.readLine();
            int[] rows = new int[1];
            int[] cols = new int[1];
            rows[0] = 0;
            cols[0] = 0;
            GridPane gp = new GridPane();
            gp.setVgap(10);
            gp.setHgap(10);

            while (next_line.contains("WORDLIST")) {
                String[] line_arr = next_line.split(":");
                String action_type = line_arr[1];
                String[] words = line_arr[2].split(",");
                Node[] n_arr = new Node[words.length + 5];

                Button delete_list_btn = new Button("X");
                delete_list_btn.setStyle("-fx-font: 15 calibri; -fx-font-weight: bold; -fx-text-fill: red");
                delete_list_btn.setMinSize(25, 20);
                delete_list_btn.setId("del_btn_" + Integer.toString(rows[0]));
                delete_list_btn.setTooltip(new Tooltip("Delete this Wordlist"));
                delete_list_btn.setOnAction(event1 -> {
                    int[] elems_to_remove = new int[1];
                    elems_to_remove[0] = 6 + cols[0];
                    int row_num = Integer.parseInt(delete_list_btn.getId().replaceAll("\\D+", ""));

                    for (Node n : gp.getChildren()) {
                        if (elems_to_remove[0] == 0) {
                            break;
                        }
                        Platform.runLater(() -> {
                            if (n.getId().equals(delete_list_btn.getId())) {
                                gp.getChildren().remove(n);
                                elems_to_remove[0]--;
                            } else if (n.getId().equals("$sctn_btn_" + row_num)) {
                                gp.getChildren().remove(n);
                                elems_to_remove[0]--;
                            } else if (n.getId().equals("$sntnce_btn_" + row_num)) {
                                gp.getChildren().remove(n);
                                elems_to_remove[0]--;
                            } else if (n.getId().equals("wds_lbl_" + row_num)) {
                                gp.getChildren().remove(n);
                                elems_to_remove[0]--;
                            } else if (n.getId().equals("frst_wd_" + row_num)) {
                                gp.getChildren().remove(n);
                                elems_to_remove[0]--;
                            } else if (n.getId().equals("more_wds_btn_" + row_num)) {
                                gp.getChildren().remove(n);
                                elems_to_remove[0]--;
                            }
                        });
                    }
                });
                n_arr[0] = delete_list_btn;

                ToggleGroup g = new ToggleGroup();

                RadioButton sentence_btn = new RadioButton("Sentence");
                sentence_btn.setToggleGroup(g);
                sentence_btn.setSelected(true);
                sentence_btn.setId("$sntnce_btn_" + Integer.toString(rows[0]));
                if (action_type.equals("SENT")) {
                    sentence_btn.setSelected(true);
                }
                n_arr[1] = sentence_btn;

                RadioButton section_btn = new RadioButton("Section");
                section_btn.setToggleGroup(g);
                section_btn.setId("$sctn_btn_" + Integer.toString(rows[0]));
                if (action_type.equals("SECT")) {
                    section_btn.setSelected(true);
                }
                n_arr[2] = section_btn;

                Label words_label = new Label(" |    Words:");
                words_label.setId("wds_lbl_" + Integer.toString(rows[0]));
                n_arr[3] = words_label;

                for (int i = 0; i < words.length; i++) {
                    TextField first_word = new TextField();
                    first_word.setMaxWidth(100);
                    first_word.setId("frst_wd_" + Integer.toString(rows[0]));
                    first_word.setText(words[i]);
                    n_arr[i + 4] = first_word;
                }

                Button more_words_btn = new Button("+");
                more_words_btn.setStyle("-fx-font: 15 calibri; -fx-font-weight: bold; -fx-text-fill: green");
                more_words_btn.setMinSize(25, 20);
                more_words_btn.setId("more_wds_btn_" + Integer.toString(rows[0]));
                more_words_btn.setTooltip(new Tooltip("Add another word to this Wordlist"));
                more_words_btn.setOnAction(event1 -> {
                    for (Node n : gp.getChildren()) {
                        if (n != null && n.getId() != null) {
                            Platform.runLater(() -> {
                                if (n.getId().equals(more_words_btn.getId())) {
                                    int r = GridPane.getRowIndex(n);
                                    int c = GridPane.getColumnIndex(n);

                                    TextField t = new TextField();
                                    t.setMaxWidth(100);
                                    int row_num = Integer.parseInt(more_words_btn.getId().replaceAll("\\D+", ""));
                                    t.setId("frst_wd_" + Integer.toString(row_num));
                                    cols[0]++;

                                    gp.add(t, c, r);
                                    gp.getChildren().remove(n);
                                    gp.add(n, c + 1, r);
                                }
                            });
                        }
                    }
                });
                n_arr[n_arr.length - 1] = more_words_btn;

                gp.addRow(rows[0], n_arr);
                rows[0]++;
                next_line = b.readLine();
            }
            wordlist_area.setContent(gp);

            if (next_line.split(":")[1].equals("YES")) {
                sample_run_checkbox.setSelected(true);
            } else {
                sample_run_checkbox.setSelected(false);
            }

            if (b.readLine().split(":")[1].equals("YES")) {
                group_checkbox.setSelected(true);
            } else {
                group_checkbox.setSelected(false);
            }

            if (b.readLine().split(":")[1].equals("YES")) {
                exclude_checkbox.setSelected(true);
            } else {
                exclude_checkbox.setSelected(false);
            }

            String[] prefs1_arr = b.readLine().split("\\|");
            if (prefs1_arr.length > 1) {
                prefs_set = true;
                prefs1 = new TextField(prefs1_arr[1]);
                prefs1.setMaxWidth(200);
            }

            String[] prefs2_arr = b.readLine().split(":");
            if (prefs2_arr.length > 1) {
                prefs_set = true;
                prefs2 = new TextField(prefs2_arr[1]);
                prefs2.setMaxWidth(45);
            }

            String[] prefs3_arr = b.readLine().split(":");
            if (prefs3_arr.length > 1) {
                prefs_set = true;
                prefs3 = new TextField(prefs3_arr[1]);
                prefs3.setMaxWidth(45);
            }

            b.close();
        } catch (IOException e) { /* do nothing */ }
    }

    private void writeSettingsFile() throws IOException {
        PrintWriter p = new PrintWriter(saved_file, "UTF-8");

        p.print("O_NAME:");
        if (!output_fname_box.getText().equals("")) {
            p.println(output_fname_box.getText());
        } else {
            p.println();
        }

        p.print("O_LOCATION|");
        if (!output_flocation_box.getText().equals("")) {
            p.println(output_flocation_box.getText());
        } else {
            p.println();
        }

        p.print("START_YEAR:");
        if (!year1_box.getSelectionModel().isEmpty()) {
            p.println(year1_box.getSelectionModel().getSelectedItem());
        } else {
            p.println();
        }

        p.print("END_YEAR:");
        if (!year2_box.getSelectionModel().isEmpty()) {
            p.println(year2_box.getSelectionModel().getSelectedItem());
        } else {
            p.println();
        }

        p.print("FILING_LIST:");
        Boolean first = true;
        for (Node n : selected_filings_gridpane.getChildren()) {
            if (n != null) {
                Label l = (Label) n;
                if (!l.getText().equals("Selected Filings:")) {
                    if (!first) {
                        p.print("," + l.getText());
                    } else {
                        p.print(l.getText());
                        first = false;
                    }
                }
            }
        }
        p.println();

        ArrayList<WordList> wordlists = getWordlists();
        for (WordList wl : wordlists) {
            p.print("WORDLIST:");
            if (wl.getActionType()) {
                p.print("SENT:");
            } else {
                p.print("SECT:");
            }

            Boolean first1 = true;
            for (int i = 0; i < wl.size(); i++) {
                if (!first1) {
                    p.print("," + wl.get(i).getWord());
                } else {
                    p.print(wl.get(i).getWord());
                    first1 = false;
                }
            }
            p.println();
        }

        p.print("CHECK1:");
        if (sample_run_checkbox.isSelected()) {
            p.println("YES");
        } else {
            p.println("NO");
        }

        p.print("CHECK2:");
        if (group_checkbox.isSelected()) {
            p.println("YES");
        } else {
            p.println("NO");
        }

        p.print("CHECK3:");
        if (exclude_checkbox.isSelected()) {
            p.println("YES");
        } else {
            p.println("NO");
        }

        p.print("PREFS1|");
        if (prefs1 != null) {
            p.println(prefs1.getText());
        } else {
            p.println();
        }

        p.print("PREFS2:");
        if (prefs2 != null) {
            p.println(prefs2.getText());
        } else {
            p.println();
        }

        p.print("PREFS3:");
        if (prefs3 != null) {
            p.println(prefs3.getText());
        } else {
            p.println();
        }

        p.close();
    }

    private File createSettingsFile() {
        FileChooser fileChooser = new FileChooser();
        FileChooser.ExtensionFilter e = new FileChooser.ExtensionFilter("SEC Crawl Files (*.s_crawl)", "*.s_crawl");
        fileChooser.getExtensionFilters().add(e);
        fileChooser.setTitle("Save Crawl Settings File");
        return fileChooser.showSaveDialog(new Stage());
    }

    private void loadWordlistArea() {
        GridPane gp = new GridPane();
        gp.setHgap(10);
        gp.setVgap(10);
        wordlist_area.setContent(gp);
        wordlist_area.setStyle("-fx-background: white; -fx-border-style: solid");
        int[] rows = new int[1];
        int[] cols = new int[1];
        rows[0] = 0;
        cols[0] = 0;

        add_wordlist_button.setOnAction(event -> addWordlist(gp, rows, cols));
    }

    private GridPane addWordlist(GridPane gp, int[] rows, int[] cols) {
        Button delete_list_btn = new Button("X");
        delete_list_btn.setStyle("-fx-font: 15 calibri; -fx-font-weight: bold; -fx-text-fill: red");
        delete_list_btn.setMinSize(25, 20);
        delete_list_btn.setId("del_btn_" + Integer.toString(rows[0]));
        delete_list_btn.setTooltip(new Tooltip("Delete this Wordlist"));

        ToggleGroup g = new ToggleGroup();

        RadioButton sentence_btn = new RadioButton("Sentence");
        sentence_btn.setToggleGroup(g);
        sentence_btn.setSelected(true);
        sentence_btn.setId("$sntnce_btn_" + Integer.toString(rows[0]));

        RadioButton section_btn = new RadioButton("Section");
        section_btn.setToggleGroup(g);
        section_btn.setId("$sctn_btn_" + Integer.toString(rows[0]));

        Label words_label = new Label(" |    Words:");
        words_label.setId("wds_lbl_" + Integer.toString(rows[0]));

        TextField first_word = new TextField();
        first_word.setMaxWidth(100);
        first_word.setId("frst_wd_" + Integer.toString(rows[0]));

        Button more_words_btn = new Button("+");
        more_words_btn.setStyle("-fx-font: 15 calibri; -fx-font-weight: bold; -fx-text-fill: green");
        more_words_btn.setMinSize(25, 20);
        more_words_btn.setId("more_wds_btn_" + Integer.toString(rows[0]));
        more_words_btn.setTooltip(new Tooltip("Add another word to this Wordlist"));

        more_words_btn.setOnAction(event1 -> {
            for (Node n : gp.getChildren()) {
                if (n != null && n.getId() != null) {
                    Platform.runLater(() -> {
                        if (n.getId().equals(more_words_btn.getId())) {
                            int r = GridPane.getRowIndex(n);
                            int c = GridPane.getColumnIndex(n);

                            TextField f = new TextField();
                            f.setMaxWidth(100);
                            int row_num = Integer.parseInt(more_words_btn.getId().replaceAll("\\D+", ""));
                            f.setId("frst_wd_" + Integer.toString(row_num));
                            cols[0]++;

                            gp.add(f, c, r);
                            gp.getChildren().remove(n);
                            gp.add(n, c + 1, r);
                        }
                    });
                }
            }
        });

        delete_list_btn.setOnAction(event1 -> {
            int[] elems_to_remove = new int[1];
            elems_to_remove[0] = 6 + cols[0];
            int row_num = Integer.parseInt(delete_list_btn.getId().replaceAll("\\D+", ""));

            for (Node n : gp.getChildren()) {
                if (elems_to_remove[0] == 0) {
                    break;
                }
                Platform.runLater(() -> {
                    if (n.getId().equals(delete_list_btn.getId())) {
                        gp.getChildren().remove(n);
                        elems_to_remove[0]--;
                    } else if (n.getId().equals("$sctn_btn_" + row_num)) {
                        gp.getChildren().remove(n);
                        elems_to_remove[0]--;
                    } else if (n.getId().equals("$sntnce_btn_" + row_num)) {
                        gp.getChildren().remove(n);
                        elems_to_remove[0]--;
                    } else if (n.getId().equals("wds_lbl_" + row_num)) {
                        gp.getChildren().remove(n);
                        elems_to_remove[0]--;
                    } else if (n.getId().equals("frst_wd_" + row_num)) {
                        gp.getChildren().remove(n);
                        elems_to_remove[0]--;
                    } else if (n.getId().equals("more_wds_btn_" + row_num)) {
                        gp.getChildren().remove(n);
                        elems_to_remove[0]--;
                    }
                });
            }
        });

        gp.addRow(rows[0], delete_list_btn, sentence_btn, section_btn, words_label, first_word, more_words_btn);
        rows[0]++;

        return gp;
    }

    private void loadFilingTypeList() throws IOException {
        InputStream filing_type_list = getClass().getResourceAsStream("filings.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(filing_type_list));

        String[] pre_s = br.readLine().split("\t");
        node1_title = pre_s[0];
        node2_title = pre_s[1];
        node3_title = pre_s[2];

        int i = 0;
        for (String line = br.readLine(); line != null; line = br.readLine()) {
            Pair<String, String> p;
            String[] s = line.split("\t");
            String filing_type = s[0];
            String filing_listed_as = s[1];
            String filing_description = s[2].replace("\"", "");

            FILING_TYPES.add(filing_type);
            p = new Pair<>(filing_listed_as, filing_description);
            FILING_DESCRIPTIONS.add(p);
        }
    }

    private void initFilingSelectionArea() {
        selected_filings_pane.setStyle("-fx-background-color: white; -fx-border-style: solid");

        select_filings_button.setOnAction((javafx.event.ActionEvent event) -> {
            GridPane gp = new GridPane();
            ScrollPane sp = new ScrollPane(gp);
            sp.setStyle("-fx-font-size: 15;");

            Label pre_n1 = new Label(node1_title);
            pre_n1.setStyle("-fx-font: 14 calibri; -fx-font-weight: bold;");
            pre_n1.setPadding(new Insets(3, 20, 3, 10));
            Label pre_n2 = new Label(node2_title);
            pre_n2.setStyle("-fx-font: 14 calibri; -fx-font-weight: bold;");
            pre_n2.setPadding(new Insets(3, 20, 3, 0));
            Label pre_n3 = new Label(node3_title);
            pre_n3.setStyle("-fx-font: 14 calibri; -fx-font-weight: bold;");
            pre_n3.setPadding(new Insets(3, 0, 3, 0));

            gp.addRow(0, pre_n1, pre_n2, pre_n3);

            for (int i = 1; i < FILING_TYPES.size(); i++) {
                Label n1 = new Label(FILING_TYPES.get(i));
                n1.setStyle("-fx-font: 14 calibri; -fx-font-style: italic;");
                n1.setPadding(new Insets(3, 20, 3, 10));
                CheckBox n2 = new CheckBox(FILING_DESCRIPTIONS.get(i).getKey());
                if (containsNode(selected_filings_gridpane, n2.getText())) {
                    n2.setSelected(true);
                }
                n2.setStyle("-fx-font: 14 calibri;");
                n2.setPadding(new Insets(3, 20, 3, 0));
                Label n3 = new Label(FILING_DESCRIPTIONS.get(i).getValue());
                n3.setStyle("-fx-font: 14 calibri;");
                n3.setPadding(new Insets(3, 0, 3, 0));

                n2.selectedProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
                    if (newValue) { // ticked
                        addFiling(n2.getText());
                    } else { // unticked
                        Boolean _break = false;
                        for (int j = 0; j < 3; j++) {
                            if (_break) {
                                break;
                            }
                            for (int k = 0; k < 5; k++) {
                                Label this_node = (Label) getNode(selected_filings_gridpane, j, k);
                                if (this_node != null && this_node.getText().equals(n2.getText())) {
                                    selected_filings_gridpane.getChildren().remove(this_node);
                                    _break = true;
                                    break;
                                }
                            }
                        }
                    }
                });
                gp.addRow(i, n1, n2, n3);
            }

            Stage stage = new Stage();
            stage.setScene(new Scene(sp, 700, 400));
            stage.setResizable(false);
            stage.setAlwaysOnTop(true);
            stage.show();
        });
    }

    private void addFiling(String s) {
        Label l = new Label(s);
        l.setStyle("-fx-font: 25 calibri; -fx-background-color: lightblue; -fx-border-style: solid");
        int r = new Random().nextInt(5);
        int c = new Random().nextInt(3);

        while (getNode(selected_filings_gridpane, c, r) != null) {
            r = new Random().nextInt(5);
            c = new Random().nextInt(3);
        }

        selected_filings_gridpane.add(l, c, r);
    }

    private void initYearArea() {
        Integer[] year_list = new Integer[22];
        for (int i = 0; i < 22; i++) {
            year_list[i] = i + 1996;
        }

        year1_box.setItems(FXCollections.observableArrayList(year_list));
        year2_box.setItems(FXCollections.observableArrayList(year_list));

        year1_box.setOnMouseClicked(event -> {
            Integer selected_year = year2_box.getValue();
            if (selected_year != null) {
                ArrayList<Integer> new_list = new ArrayList<>();
                for (Integer yr : year_list) {
                    if (yr <= selected_year) {
                        new_list.add(yr);
                    }
                }
                year1_box.setItems(FXCollections.observableArrayList(new_list));
            }
        });

        year2_box.setOnMouseClicked(event -> {
            Integer selected_year = year1_box.getValue();
            if (selected_year != null) {
                ArrayList<Integer> new_list = new ArrayList<>();
                for (Integer yr : year_list) {
                    if (yr >= selected_year) {
                        new_list.add(yr);
                    }
                }
                year2_box.setItems(FXCollections.observableArrayList(new_list));
            }
        });
    }

    private void initOutputArea() {
        browse_button.setOnAction(event -> {
            DirectoryChooser directory_chooser = new DirectoryChooser();
            Stage stage = new Stage();
            stage.setTitle("Choose Directory");
            directory_chooser.setTitle("Choose Directory");
            File default_directory = new File(System.getProperty("user.dir"));
            directory_chooser.setInitialDirectory(default_directory);
            File selected_directory = directory_chooser.showDialog(stage);

            try {
                output_flocation_box.setText(selected_directory.toPath().toString());
            } catch (NullPointerException e) { /* do nothing */ }
        });
    }

    private Node getNode(GridPane gp, int col, int row) {
        for (Node node : gp.getChildren()) {
            if (GridPane.getColumnIndex(node) == col && GridPane.getRowIndex(node) == row) {
                return node;
            }
        }
        return null;
    }

    private Boolean containsNode(GridPane gp, String s) {
        for (Node n : gp.getChildren()) {
            Label l = (Label) n;
            if (l.getText().equals(s)) {
                return true;
            }
        }
        return false;
    }
}
