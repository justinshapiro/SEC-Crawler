package sec_crawler;

import java.util.*;

class WordList {
    private ArrayList<Word> LIST;
    private Boolean ACTION_TYPE; // true = sentence, false = section
    private ArrayList<ArrayList<Integer>> PROXIMITY_SET;

    WordList() {
        LIST = new ArrayList<>();
        ACTION_TYPE = true;
        PROXIMITY_SET = new ArrayList<>();
    }

    WordList(ArrayList<String> str_list, Boolean action) {
        for (String s : str_list) {
            LIST.add(stringToWord(s));
        }
        ACTION_TYPE = action;
        PROXIMITY_SET = new ArrayList<>();
    }

    ArrayList<ArrayList<Integer>> getProximitySet(int threshold) {
        /*
         * A Proximity Set is a set whose members belong in only one set that does not
         * contain any other members that share the same Proximity Set. Members of a Proximity Set
         * are in defined proximity, within a distance m each other.
         * The cardinality of a proximity set is equal to the amount of sets that form it.
         */

        // if any of the lists are empty, there cannot be a proximity set
        Boolean isFull = true;
        for (Word w : LIST) {
            if (w.getInstances().isEmpty()) {
                isFull = false;
            }
        }

        // if all lists have size of at least one:
        if (isFull) {
            // lists arrive sorted in increasing order, sort all lists based on their first element
            LIST.sort(Comparator.comparing(w -> w.getInstances().get(0)));

            // create index sets that specifically iterate each list independently
            ArrayList<Integer> idx_set = new ArrayList<>();
            for (int i = 0; i < LIST.size(); i++) {
                idx_set.add(0);
            }

            // if the index corresponding to its list is grows to be greater than
            // the size of the list, all indices are not valid and no Proximity Set can exist
            Boolean listEnd = false;
            for (int j = 0; j < LIST.size(); j++) {
                if (idx_set.get(j) >= LIST.get(j).getInstances().size()) {
                    listEnd = true;
                }
            }

            // no sets are empty and all indices in the index set are valid, so we search for the Proximity Set
            if (!listEnd) {
                // traverse the lists:
                Boolean good_traverse = true;
                int last_inst = LIST.get(0).getInstances().get(idx_set.get(0));
                for (int j = 1; j < LIST.size(); j++) {
                    int curr_inst = LIST.get(j).getInstances().get(idx_set.get(j));
                    if (Math.abs(curr_inst - last_inst) > threshold) {
                        // traverse segment fails,
                        good_traverse = false;
                        // change the faulting index and start another traverse from the beginning
                        idx_set.set(j, idx_set.get(j) + 1);
                        break;
                    } else {
                        // current traverse segment succeeds, resume traverse
                        last_inst = curr_inst;
                    }
                }

                // if the traverse was successful, we have found a Proximity Set
                if (good_traverse) {
                    ArrayList<Integer> curr_proximity = new ArrayList<>();
                    for (int j = 0; j < idx_set.size(); j++) {
                        curr_proximity.add(LIST.get(j).getInstances().get(idx_set.get(j)));
                    }

                    PROXIMITY_SET.add(curr_proximity);

                    // increment all indices by one
                    for (int k = 0; k < idx_set.size(); k++) {
                        idx_set.set(k, idx_set.get(k) + 1);
                    }
                }
            }
        }

        return PROXIMITY_SET;
    }

    void addWord(String s) {
        LIST.add(stringToWord(s));
    }

    void setActionType(Boolean action) {
        ACTION_TYPE = action;
    }

    private Word stringToWord(String s) {
        Boolean compound = false;
        if (s.contains("|")) {
            compound = true;
        }
        return new Word(s, compound);
    }

    private String wordToString(Word w) {
        return w.getWord();
    }

    ArrayList<String> getList() {
        ArrayList<String> str_list = new ArrayList<>();

        for (Word w : LIST) {
            str_list.add(wordToString(w));
        }

        return str_list;
    }

    Boolean getActionType() {
        return ACTION_TYPE;
    }

    Integer size() {
        return LIST.size();
    }

    Word get(Integer i) {
        return LIST.get(i);
    }

    void clearAllInstances() {
        for (Word w : LIST) {
            w.clearInstances();
        }
        PROXIMITY_SET.clear();
    }
}

class Word {
    private String WORD;
    private ArrayList<Integer> INSTANCES;
    private Boolean WORD_TYPE; // true = compound, false = single

    Word(String s, Boolean compound) {
        INSTANCES = new ArrayList<>();
        WORD = s.toLowerCase();
        WORD_TYPE = compound;
    }

    String getWord() {
        return WORD;
    }

    Boolean isCompound() {
        return WORD_TYPE;
    }

    ArrayList<Integer> getInstances() {
        return INSTANCES;
    }

    void addInstance(Integer i) {
        INSTANCES.add(i);
    }

    void clearInstances() {
        INSTANCES.clear();
    }

    String[] getCompoundWord() {
        return WORD.split("\\|");
    }
}