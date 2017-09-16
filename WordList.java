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
        ArrayList<ArrayList<Integer>> instanceSets = new ArrayList<>();

        // if any of the instance sets are empty, a proximity set cannot exist
        Boolean noSetsAreEmpty = true;
        for (Word w : LIST) {
            if (w.getInstances().isEmpty()) {
                noSetsAreEmpty = false;
                break;
            } else {
                instanceSets.add(w.getInstances());
            }
        }

        // if none of the instance sets are empty, proceed
        if (noSetsAreEmpty) {
            // sort all instance sets based on their first element
            instanceSets.sort(Comparator.comparing(w -> w.get(0)));
            Boolean endOfInstanceSet = false;

            // the pivot set is the instance set whose first element is the lowest out of all the instance sets
            ArrayList<Integer> pivotSet = instanceSets.get(0);
            int pivot_element;

            // the trial set is the first set we attempt to traverse
            ArrayList<Integer> trialSet;
            int trial_element;

            // the candidate set keeps track of the traverse and is a potential subset of the proximity set
            ArrayList<Integer> candidateSet = new ArrayList<>();
            Boolean testCandidateSet = false;
            int traversing_idx = 1;

            while (!endOfInstanceSet) {
                pivot_element = pivotSet.get(0);
                trialSet = instanceSets.get(traversing_idx);

                for (int i = 0; i < trialSet.size(); i++) {
                    trial_element = trialSet.get(i);
                    int curr_dist = Math.abs(pivot_element - trial_element);

                    // if the distance between numbers is not within the threshold,
                    if (curr_dist > threshold) {
                        // if the current_element is greater than or equal to the pivot, the current traverse is bad
                        if (trial_element >= pivot_element) {
                            pivotSet.remove(0);

                            if (pivotSet.isEmpty()) {
                                endOfInstanceSet = true;
                            } else {
                                instanceSets.set(0, pivotSet);
                            }

                            candidateSet.clear();
                            traversing_idx = 1;
                            break;
                        }
                    }

                    // the distance between elements is within the threshold, so we continue the traverse
                    else {
                        candidateSet.add(trial_element);
                        if (traversing_idx == instanceSets.size() - 1) {
                            testCandidateSet = true;
                        } else {
                            traversing_idx++;
                        }
                        break;
                    }

                    if (i == trialSet.size() - 1) {
                        endOfInstanceSet = true;
                    }
                }

                if (testCandidateSet) {
                    int curr_dist = 0;
                    for (int i = 0; i < candidateSet.size(); i++) {
                        for (int j = 0; j < candidateSet.size(); j++) {
                            if (i != j) {
                                int element_1 = candidateSet.get(i);
                                int element_2 = candidateSet.get(j);
                                curr_dist = Math.abs(element_1 - element_2);

                                if (curr_dist > threshold) {
                                    break;
                                }
                            }
                        }

                        if (curr_dist > threshold) {
                            break;
                        }
                    }

                    if (curr_dist <= threshold) {
                        candidateSet.add(0, pivot_element);
                        PROXIMITY_SET.add(new ArrayList<>(candidateSet));
                    }

                    for (Integer candidate_element : candidateSet) {
                        for (ArrayList<Integer> instanceSet : instanceSets) {
                            if (instanceSet.contains(candidate_element)) {
                                instanceSet.remove(candidate_element);
                                if (instanceSet.isEmpty()) {
                                    endOfInstanceSet = true;
                                    break;
                                }
                            }
                        }

                        if (endOfInstanceSet) {
                            break;
                        }
                    }

                    if (pivotSet.isEmpty()) {
                        endOfInstanceSet = true;
                    }

                    candidateSet.clear();
                    traversing_idx = 1;
                    testCandidateSet = false;
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