import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.lang.*;

public class SpellCorrector {
    final private CorpusReader cr;
    final private ConfusionMatrixReader cmr;
    
    final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz'".toCharArray();
    
    
    public SpellCorrector(CorpusReader cr, ConfusionMatrixReader cmr) 
    {
        this.cr = cr;
        this.cmr = cmr;
    }
    
    public String correctPhrase(String phrase)
    {
        if(phrase == null || phrase.length() == 0)
        {
            throw new IllegalArgumentException("phrase must be non-empty.");
        }
            
        String[] words = phrase.split(" ");
        int correctedWords = 0;
        String[] suggestion;
        String finalSuggestion = "";
        double highestProb;
        
        /*
            First, fix the words that are not in the dictionary.
        */
        for(int i = 0; i < words.length; i++) {
            if(!cr.inVocabulary(words[i])) {
                highestProb = Integer.MIN_VALUE;
                String correction = "";
                Map<String,Double> candidates = getCandidateWords(words[i]);
                for(String canWord : candidates.keySet()) {
                    double prob = Math.log(candidates.get(canWord));
                    if(i != 0) {
                        prob = prob + Math.log(cr.getSmoothedCount(words[i - 1] + " " 
                                + canWord));
                    }
                    if(i != (words.length - 1)) {
                        prob = prob + Math.log(cr.getSmoothedCount(canWord + " " +
                                words[i + 1]));
                    }
                    if(prob > highestProb) {
                        highestProb = prob;
                        correction = canWord;
                    }
                }
                if (correction.equals("")) {
                    throw new IllegalStateException("No suitable candidate");
                }
                words[i] = correction;
                correctedWords++;
                if (correctedWords == 2) {
                    finalSuggestion = String.join(" ", words);
                    return finalSuggestion.trim();
                }
            }
        }
        
        //get all combinations of words that contain an error
        HashSet<boolean[]> errorCombinations = 
                getErrorCombinations(correctedWords, words.length, phrase, 2);
        
        // look for best candidate sentences
        Map<String[],double[]> candidateSentences = new HashMap();
        for (boolean[] errorCombi: errorCombinations) {
            getCandidateSentence(errorCombi, candidateSentences, words);
        }
        
        //evaluate the candidate sentences and pick the best
        highestProb = Integer.MIN_VALUE;
        suggestion = words;
        for(String[] canSen : candidateSentences.keySet()) { //evaluate each candidate
            double prob;
            prob = Math.log(evaluateBigramSentence(canSen)); 
            // use the noisy channel probabilities for the corrected words
            for(double noisyProb: candidateSentences.get(canSen)) { 
                if(Double.compare(noisyProb, 1.0) != 0) { //corrected word
                    prob += Math.log(noisyProb);
                }
            }
            if (prob > highestProb) { //new best candidate
                highestProb = prob;
                suggestion = canSen;
            }
        }
        
        finalSuggestion = String.join(" ", suggestion);
        
        return finalSuggestion.trim();
    }    
    
    //combines the probabilities for all the bigrams in a sentence
    public double evaluateBigramSentence(String[] words) {
        double prob = 0;
        for(int i = 0; i < words.length; i++) {
            //prob bigram with word in front
            if(i != 0) {
                prob = prob + Math.log(cr.getSmoothedCount(words[i - 1] + " " 
                        + words[i]));
            }
            //prob bigram with word afterwards
            if(i != (words.length - 1)) {
                prob = prob + Math.log(cr.getSmoothedCount(words[i] + " " +
                        words[i + 1]));
            }
        }
        return prob;
    }
    
    /**
     * This method adds the best possible sentence under the assumption words
     * specified in errorCombination are wrong.
     * 
     * @param errorCombination indicates which words in the sentence are considered wrong
     * @param sentences map of sentences it will add a sentence to
     * @param words orignal sentence
     */
    public void getCandidateSentence(boolean[] errorCombination, Map sentences, String[] words) {
        double[] probabilities = new double[words.length]; //noisy channel probabilities per word
        String[] newSentence = words.clone();
        for(int i = 0; i < words.length; i++) {
            if (errorCombination[i] == true) { //assume word is wrong
                double prob;
                double highestProb = Integer.MIN_VALUE;
                String finalCandidate = "";
                Map<String,Double> candidates = getCandidateWords(words[i]);
                //loop over all candidate words and determine the best option
                for(String canWord : candidates.keySet()) {
                    prob = 0;
                    //prob bigram with word in front
                    if(i != 0) {
                        prob = prob + Math.log(cr.getSmoothedCount(words[i - 1] + " " 
                                + canWord));
                    }
                    //prob bigram with word afterwords
                    if(i != (words.length - 1)) {
                        prob = prob + Math.log(cr.getSmoothedCount(canWord + " " +
                                words[i + 1]));
                    }
                    prob += Math.log(candidates.get(canWord)); //noisy channel prob
                    if (prob > highestProb) { //found new best candidate
                        highestProb = prob;
                        finalCandidate = canWord;
                        probabilities[i] = prob;
                    }
                }
                newSentence[i] = finalCandidate; //correct sentence
                if (finalCandidate == "") {
                    throw new IllegalStateException("no suitable candidate");
                }
            } else { //word assumed correct
                probabilities[i] = 1.0;
            }
        }
        sentences.put(newSentence, probabilities); //add sentence to map
    }
    
    /**
     * Generates all combinations of positions where faulty words can be located
     * given the correctedWords and the phrase-length constraints.
     * 
     * @param correctedWords
     * @param length
     * @return 
     */
    public HashSet<boolean[]> getErrorCombinations(int correctedWords, 
            int length, String initialPhrase, int maximumCorrections) {
        
        // get initial corrections
        boolean[] initialCorrections = new boolean[length];
        String[] wordsInInitialPhrase = initialPhrase.trim().split(" ");
        for (int word = 0; word < length; word++) {
            if (!cr.inVocabulary(wordsInInitialPhrase[word])) {
                initialCorrections[word] = true;
            } else {
                initialCorrections[word] = false;
            }
        }
        
        // return a hashset with all possible corrections
        return recursiveErrorCombinations(correctedWords, length, initialCorrections,
                maximumCorrections);
    }
    
    public HashSet<boolean[]> recursiveErrorCombinations(int correctedWords, 
            int length, boolean[] corrections, int maximumCorrections) {
        if (correctedWords == maximumCorrections) { // leaf
            HashSet<boolean[]> list = new HashSet<boolean[]>();
            list.add(corrections);
            return list;
        } else { // add one correction
            HashSet<boolean[]> list = new HashSet<boolean[]>();
            list.add(corrections);
            for (int word = 0; word < length; word++) {
                // check if this word as well as the word before and after have
                // not been corrected
                if (corrections[word] == false 
                        && corrections[Math.max(0, word - 1)] == false
                        && corrections[Math.min(length - 1, word + 1)] == false) 
                {
                    boolean[] newCorrections = corrections.clone();
                    newCorrections[word] = true;
                    // add this combination
                    list.add(newCorrections);
                    // add children combinations
                    list.addAll(recursiveErrorCombinations(correctedWords + 1,
                            length, newCorrections, maximumCorrections));
                }
            }
            return list;
        }
    }

    /**
     * Returns a map with candidate words and their noisy channel probability.
     * 
     * @param word word to find candidates for
     * @return map with candidate words and their noisy channel probability
     */
    public Map<String,Double> getCandidateWords(String word)
    {
        Map<String,Double> mapOfWords = new HashMap<>();
        
        Set<String> candidateWords = new HashSet();
        String newWord;
        int confusion;
        int count;
        double probability;
        
        // add this word if it is correctly spelled with probability 0.95
        if (this.cr.inVocabulary(word)) {
            mapOfWords.put(word, 0.95);
        }
        
        // fix by insertion
        for(int i = 0; i <= word.length(); i++) { // for all characters in word
            for(char c : ALPHABET) { // for all characters in alphabet
                // generate new word where c is inserted at position i
                newWord = word.substring(0, i) + c;
                newWord = newWord + word.substring(i, word.length());
                // if the new word exists, determine its probability
                if (this.cr.inVocabulary(newWord)) {
                    String letterInFront = " ";
                    if (i - 1 > 0) {
                        letterInFront = String.valueOf(word.charAt(i - 1));
                    }
                    // look up in confusion matrix
                    confusion = this.cmr.getConfusionCount(
                        letterInFront, 
                        letterInFront + c
                    );
                    // look up the total occurrence of the correct combination
                    count = this.cmr.getCharCount(letterInFront + c);
                    probability = (double) confusion / (double) count;
                    // add candidate word with probability to the map
                    mapOfWords.put(newWord, probability);
                }
            }
        }
        
        for(int i = 0; i < word.length(); i++) { // for all characters in word
            // fix by substitution
            for(char c : ALPHABET) { // for all characters in alphabet
                // generate new word where word[i] is substituted with c
                newWord = word.substring(0, i) + c;
                newWord = newWord + word.substring(i + 1, word.length());
                // if the new word exists, determine its probability
                if (this.cr.inVocabulary(newWord)) {
                    // look up in the confusion matrix
                    confusion = this.cmr.getConfusionCount(
                            String.valueOf(word.charAt(i)), String.valueOf(c)
                    );
                    // look up the total occurrence of the correct combination
                    count = this.cmr.getCharCount(String.valueOf(c));
                    probability = (double) confusion / (double) count;
                    // add candidate word with probability to the map
                    mapOfWords.put(newWord, probability);
                }
            }
            
            // fix by deletion
            // generate new word where character i is deleted
            newWord = word.substring(0, i);
            newWord = newWord + word.substring(i + 1, word.length());
            // if the new word exists, determine its probability
            if (this.cr.inVocabulary(newWord)) {
                // look up in the confusion matrix
                confusion = this.cmr.getConfusionCount(
                        " " + String.valueOf(word.charAt(i)), " "
                );
                // look up the total occurrence of the correct combination
                count = this.cmr.getCharCount(" ");
                probability = (double) confusion / (double) count;
                // add candidate word with probability to the map
                mapOfWords.put(newWord, probability);
            }
        }
              
        // fix by transposition
        for(int i = 0; i < word.length() - 1; i++) { // for all characters in word
            // generate new word where character i is switched with character i+1
            newWord = word.substring(0, i) + word.charAt(i + 1) + word.charAt(i)
                    + word.substring(i+2, word.length());
            // if the new word exists, determine its probability
            if (this.cr.inVocabulary(newWord)) {
                // look up in the confusion matrix
                confusion = this.cmr.getConfusionCount(
                        word.substring(i, i + 2), 
                        String.valueOf(word.charAt(i + 1)) + 
                                String.valueOf(word.charAt(i))
                );
                // look up the total occurrence of the correct combination
                count = this.cmr.getCharCount(word.substring(i, i + 2));
                probability = (double) confusion / (double) count;
                // add candidate word with probability to the map
                mapOfWords.put(newWord, probability);
            }
        }       

        return mapOfWords;
    }            
}