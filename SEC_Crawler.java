package sec_crawler;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.util.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.*;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

class SEC_Crawler {
    // Globals to store user data & result
    private ArrayList<String> RESULT_ARRAY;
    private ArrayList<String> FILING_TYPES;
    private ArrayList<Integer> YEARS;
    private ArrayList<WordList> WORDLISTS;
    private final ArrayList<Integer> QUARTERS = new ArrayList<>(Arrays.asList(1, 2, 3, 4));
    private String EDGAR_BASE_PATH = "https://sec.gov/Archives/";
    private int SENTENCE_THRESHOLD = 5;
    private int SECTION_THRESHOLD = 20;
    private File RESULT_FILE;
    private Boolean[] OPTIONS;
    private Controller ui;

    // Overloaded Constructor
    SEC_Crawler(ArrayList<String> filing_types, Pair<Integer, Integer> years, ArrayList<WordList> wordlists, String result_file, Controller c) throws IOException {
        // Initialize globals
        RESULT_ARRAY = new ArrayList<>();
        FILING_TYPES = new ArrayList<>();
        WORDLISTS = new ArrayList<>();
        YEARS = new ArrayList<>();
        RESULT_FILE = get_result_file(result_file);
        OPTIONS = new Boolean[3];
        OPTIONS[0] = false;
        OPTIONS[1] = false;
        OPTIONS[2] = false;
        ui = c;

        // Assign user data to globals
        FILING_TYPES = filing_types;
        WORDLISTS = wordlists;

        // obtain years from the range the user specified
        int num_years = years.getValue() - years.getKey();
        int first_year = years.getKey();
        for (int i = 0; i <= num_years; i++) {
            YEARS.add(first_year + i);
        }
    }

    // set additional options for crawling
    void setOptions(Boolean[] options) {
        OPTIONS = options;
    }

    // set user-defined preferences
    void setPreferences(String pref1, String pref2, String pref3) {
        if (pref1.charAt(pref1.length() - 1) != '/') {
            pref1 = pref1 + "/";
        }

        EDGAR_BASE_PATH = pref1;
        SENTENCE_THRESHOLD = Integer.parseInt(pref2);
        SECTION_THRESHOLD = Integer.parseInt(pref3);
    }

    // Main method to crawl SEC to obtain data based on frequency of related words
    void crawl() throws IOException {
        // send preamble to status bar
        send_progress_to_ui(-1);

        // fill array with URLs of user-specified filings
        ArrayList<Pair<String, String>> filings = getFilingURLs();
        int filing_count = 0;

        // Prepare result file
        PrintWriter CSV = new PrintWriter(RESULT_FILE);
        CSV.print("CIK,Acceptance Datetime,Filing Type,URL");
        for (int i = 0; i < WORDLISTS.size(); i++) {
            CSV.print(",Wordlist " + Integer.toString(i + 1) + " Result [");
            for (int j = 0; j < WORDLISTS.get(i).size(); j++) {
                CSV.print(WORDLISTS.get(i).get(j).getWord() + "+");
            }
            CSV.print("]");
        }
        CSV.println();
        CSV.close();

        // perform crawling algorithm on each filing
        for (Pair<String, String> filing_info : filings) {
            ArrayList<String> curr_filing = new ArrayList<>();
            String filing_address = filing_info.getKey();
            String filing_type = filing_info.getValue();
            String acceptance_date = "";
            String cik_str = filing_address.split("/")[6];

            URL filing = new URL(filing_address);

            // wait out server for CloudFlare check
            HttpURLConnection connection = (HttpURLConnection) filing.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            while (connection.getResponseCode() == 503) {
                connection.disconnect();
                connection = (HttpURLConnection) filing.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();
            }

            // get the line number of each occurrence of each word in the wordlist for the given filing
            try {
                BufferedReader read_filing = new BufferedReader(new InputStreamReader(filing.openStream()));
                int line_num = 0;
                Boolean acceptance_datetime_found = false;
                for (String line = read_filing.readLine(); line != null; line = read_filing.readLine()) {
                    curr_filing.add(line);
                    // extract the acceptance date
                    if (!acceptance_datetime_found) {
                        if (line.contains("<ACCEPTANCE-DATETIME>")) {
                            line = line.replace("<ACCEPTANCE-DATETIME>", "");
                            acceptance_date = dateParse(line);
                            acceptance_datetime_found = true;
                        }
                    }

                    // get the occurrences of each word in wordlist
                    for (WordList list : WORDLISTS) {
                        for (int i = 0; i < list.size(); i++) {
                            if (list.get(i).isCompound()) {
                                String[] multiword = list.get(i).getCompoundWord();
                                for (String word : multiword) {
                                    if (line.toLowerCase().contains(word)) {
                                        list.get(i).addInstance(line_num);
                                    }
                                }
                            } else {
                                if ((line.toLowerCase().contains(list.get(i).getWord()))) {
                                    list.get(i).addInstance(line_num);
                                }
                            }
                        }
                    }
                    line_num++;
                }
            } catch (IOException e) { /* do nothing */ }

            String csv_line = cik_str + "," + acceptance_date + "," + filing_type + ",";
            csv_line += filing_address.replace(".txt", "-index.htm") + ",";
            ArrayList<String> valid_extracted_data = new ArrayList<>();
            for (WordList list : WORDLISTS) {
                ArrayList<ArrayList<Integer>> proximity_set;
                if (list.getActionType()) {
                    proximity_set = list.getProximitySet(SENTENCE_THRESHOLD);
                } else {
                    proximity_set = list.getProximitySet(SECTION_THRESHOLD);
                }

                String extracted_data = "";
                if (!proximity_set.isEmpty()) {
                    extracted_data = getDataFromProximitySet(curr_filing, proximity_set, list.getActionType(), list);
                }

                if (filing_address.equals("https://sec.gov/Archives/edgar/data/1001385/0001193125-11-013006.txt")) {
                    String s = "";
                }

                if (extracted_data.equals("")) {
                    csv_line += ",";
                } else {
                    csv_line += "\"" + extracted_data + "\",";
                    valid_extracted_data.add(extracted_data);
                }
            }

            CSV = new PrintWriter(new FileOutputStream(RESULT_FILE, true));
            if (OPTIONS[2] && !valid_extracted_data.isEmpty()) {
                CSV.println(csv_line);
            } else if (!OPTIONS[2]) {
                CSV.println(csv_line);
            }
            CSV.close();

            for (WordList WORDLIST : WORDLISTS) {
                WORDLIST.clearAllInstances();
            }

            curr_filing.clear();

            filing_count++;
            send_progress_to_ui((float) filing_count / (float) filings.size());
        }
    }

    // Convert a Proximity Set to extracted data
    private String getDataFromProximitySet(ArrayList<String> data_source, ArrayList<ArrayList<Integer>> proximity_set,
                                           Boolean actionType, WordList wl) throws IOException {
        String data = "";

        // If we even have a Proximity Set, proceed
        if (!proximity_set.isEmpty()) {
            for (ArrayList<Integer> prox_set : proximity_set) {
                Collections.sort(prox_set);
                int start_num = prox_set.get(0);
                int end_num = prox_set.get(prox_set.size() - 1);

                // Data extraction for sentences
                if (actionType) {
                    StringBuilder sample_area = new StringBuilder();

                    // Put all data corresponding to the Proximity Set in a string
                    int i = 0;
                    while (i <= end_num - start_num) {
                        sample_area.append(getLine(start_num + i, data_source)).append(" ");
                        i++;
                    }

                    // Split data by the sentences in it
                    String s_sent = sample_area.toString();
                    s_sent = handleUnclosedTags(s_sent);
                    s_sent = Jsoup.parse(s_sent).text().replace("\t", " ");
                    s_sent = s_sent.replaceAll("[^\\x00-\\x7f]", "");
                    String[] sentences = s_sent.split("\\.\\s+");
                    sample_area = new StringBuilder(s_sent);

                    // Keep adding lines above and below the Proximity Set ("pump" it up) until we have a three sentence sample
                    int d = 1;
                    while (sentences.length < 3) {
                        sample_area.append(getLine(start_num + i, data_source)).append(" ");
                        i++;
                        sample_area.insert(0, " " + getLine(start_num - d, data_source));
                        d++;
                        String pre_sentence_str = handleUnclosedTags(sample_area.toString());
                        pre_sentence_str = pre_sentence_str.replace("\t", " ");
                        pre_sentence_str = pre_sentence_str.replaceAll("[^\\x00-\\x7f]", "");
                        sentences = Jsoup.parse(pre_sentence_str).text().split("\\.\\s+");
                    }

                    // Extract the target sentence
                    int target = -1;
                    for (int z = 0; z < sentences.length; z++) {
                        Boolean all_words = true;
                        for (int j = 0; j < wl.size(); j++) {
                            if (!sentences[z].contains(wl.get(j).getWord())) {
                                all_words = false;
                                break;
                            }
                        }

                        if (all_words) {
                            target = z;
                            break;
                        }
                    }

                    // Prepare the sentence
                    if (target != -1) {
                        data = sentences[target] + ".";
                    } else {
                        data = "";
                    }
                }

                // Data extraction for sections
                else {
                    StringBuilder sample_area = new StringBuilder();
                    Pair<Integer, Integer> section_bounds = encircleTag(start_num, end_num, "<div", data_source);

                    // if <div> tags are used for section breaks, get the bounds
                    if (section_bounds.getKey() != -1) {
                        start_num = section_bounds.getKey();
                        end_num = section_bounds.getValue();
                    }

                    // otherwise, return the enclosing paragraph as a workaround
                    else {
                        section_bounds = encircleTag(start_num, end_num, "<p", data_source);
                        start_num = section_bounds.getKey();
                        end_num = section_bounds.getValue();
                    }

                    int i = 0;
                    while (i <= end_num - start_num) {
                        sample_area.append(getLine(start_num + i, data_source)).append(" ");
                        i++;
                    }

                    // remove HTML tags
                    data = Jsoup.parse(sample_area.toString()).text();

                    // Remove partial sentences
                    data = data.replace("\t", " ").replaceAll("[^\\x00-\\x7f]", "");
                    String[] d_arr = data.split("\\.\\s+");
                    if (d_arr.length > 0) {
                        if (d_arr[0].length() < 40) {
                            d_arr[0] = "";
                        } else {
                            Character c = d_arr[0].replace(" ", "").charAt(0);
                            if (!Character.isUpperCase(c)) {
                                d_arr[0] = "";
                            }
                        }
                        if (d_arr[d_arr.length - 1].length() < 40) {
                            d_arr[d_arr.length - 1] = "";
                        } else {
                            Character c = d_arr[d_arr.length - 1].replace(" ", "").charAt(0);
                            if (!Character.isUpperCase(c)) {
                                d_arr[d_arr.length - 1] = "";
                            }
                        }
                    }

                    // write the data without partial sentences
                    data = "";
                    for (String d : d_arr) {
                        if (!d.equals("")) {
                            data += d + ".";
                        }
                    }
                }
            }
        }

        return data;
    }

    // Return the endpoints of the specified tag
    private Pair<Integer, Integer> encircleTag(int initial_start, int initial_end, String tag, ArrayList<String> data_source)
            throws IOException {
        Pair<Integer, Integer> p;
        Boolean foundUpperTag = false;
        Boolean foundLowerTag = false;
        int i = 0;
        int j = 0;

        // look for the upper tag until we find it or reach the beginning of the file
        while (!foundUpperTag && initial_start - i != 0) {
            if (getLine(initial_start - i, data_source).toLowerCase().contains(tag)) {
                foundUpperTag = true;
            } else {
                i++;
            }
        }

        // if the upper tag was found, do the same thing for the lower tag
        if (foundUpperTag) {
            tag = tag.charAt(0) + "/" + tag.substring(1, tag.length());
            while (!foundLowerTag && getLine(initial_start + j, data_source) != null) {
                if (getLine(initial_start + j, data_source).toLowerCase().contains(tag)) {
                    foundLowerTag = true;
                } else {
                    j++;
                }
            }
        }

        // return the bounds enclosed by the tag if both tags were found, or -1 otherwise
        if (foundUpperTag && foundLowerTag) {
            p = new Pair<>(initial_start - i, initial_end + j);
        } else {
            p = new Pair<>(-1, -1);
        }

        return p;
    }

    // Get a single line from a file
    private String getLine(int line_num, ArrayList<String> data_source) {
        String ret_line = "";
        for (int i = 0; i < data_source.size(); i++) {
            if (i == line_num) {
                ret_line = data_source.get(i);
                break;
            }
        }
        return ret_line;
    }

    // Deal with unclosed HTML tags, something JSOUP doesn't do
    private String handleUnclosedTags(String s) {
        String modified_s = s;
        int start_tags = s.length() - s.replace("<", "").length();
        int end_tags = s.length() - s.replace(">", "").length();

        if (start_tags != end_tags) {
            if (start_tags < end_tags) {
                if (s.charAt(0) == ' ') {
                    s = s.substring(1, s.length());
                }
                modified_s = "<" + s;
            } else {
                modified_s = s + ">";
            }
        }

        return modified_s;
    }

    // Returns array of URLs pointing to HTML or text versions of filings
    private ArrayList<Pair<String, String>> getFilingURLs() throws IOException {
        // we need an array to store the filing URLs we wish to return
        ArrayList<Pair<String, String>> filing_urls = new ArrayList<>();

        // For OPTIONS[0] = true
        int count = 0;

        // iterate through the master list of filings for each year looking for URLs
        for (Integer year : YEARS) {
            if (OPTIONS[0] && count == 100) {
                break;
            }

            for (Integer quarter : QUARTERS) {
                // open master file for given quarter in year for reading
                String url_str = EDGAR_BASE_PATH + "edgar/full-index/" + year + "/QTR" + quarter + "/master.idx";
                URL master_url = new URL(url_str);
                BufferedReader read_master = new BufferedReader(new InputStreamReader(master_url.openStream()));

                // read from the master file for the given quarter in the given year
                for (String line = read_master.readLine(); line != null; line = read_master.readLine()) {
                    String[] line_arr = line.split("\\|");
                    if (line_arr.length == 5) {
                        if (FILING_TYPES.contains(line_arr[2]) || FILING_TYPES.contains(line_arr[2] + "/A")) {
                            Pair<String, String> url_found = new Pair<>(EDGAR_BASE_PATH + line_arr[4], line_arr[2]);
                            filing_urls.add(url_found);

                            if (OPTIONS[0]) {
                                count++;
                            }
                        }
                    }

                    if (OPTIONS[0] && count == 100) {
                        break;
                    }
                }

                if (OPTIONS[0] && count == 100) {
                    break;
                }
            }
        }

        if (OPTIONS[1]) {
            filing_urls.sort(Comparator.comparing(Pair::getValue));
        }

        return filing_urls;
    }

    // Create the result file, enumerating if one of the same name already exists
    private File get_result_file(String f) throws IOException {
        File result_file = new File(f);

        if (!result_file.createNewFile()) {
            for (int i = 0; !result_file.createNewFile(); i++) {
                result_file = new File(f.replace(".csv", " (") + Integer.toString(i + 1) + ").csv");
            }
        }

        return result_file;
    }

    // Convert the ACCEPTANCE-DATETIME to a readable format
    private String dateParse(String raw_date) {
        String year = raw_date.substring(0, 4);
        String month = raw_date.substring(4, 6);
        String day = raw_date.substring(6, 8);
        String HH = raw_date.substring(8, 10);
        String MM = raw_date.substring(10, 12);
        String SS = raw_date.substring(12, 14);
        return month + "/" + day + "/" + year + " " + HH + ":" + MM + ":" + SS;
    }

    // Send status update to UI
    private void send_progress_to_ui(float percent_complete) {
        new Thread(new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Platform.runLater(() -> ui.progress_bar.setProgress(percent_complete));
                return null;
            }
        }).start();
    }
}