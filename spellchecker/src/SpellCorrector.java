import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SpellCorrector {
    final private CorpusReader cr;
    final private ConfusionMatrixReader cmr;
    
    final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz'".toCharArray();
        
    /**
     * Constructor.
     * 
     * @param cr CorpusReader
     * @param cmr ConfusionMatrixReader
     */
    public SpellCorrector(CorpusReader cr, ConfusionMatrixReader cmr) {
        this.cr = cr;
        this.cmr = cmr;
    }
    
    /**
     * Corrects a phrase.
     * 
     * @param phrase possibly incorrectly spelled phrase.
     * @return plausible correction for the phrase.
     */
    public String correctPhrase(String phrase) {
        if (phrase == null || phrase.length() == 0) {
            throw new IllegalArgumentException("phrase must be non-empty.");
        }

        // split the phrase into its words
        String[] words = phrase.split(" ");
        // counter for corrected words
        int correctedWords = 0;
        
        String[] suggestion;
        String finalSuggestion = "";
        double highestProb;
        
        // Fix the words that are not in the vocabulary
        for (int i = 0; i < words.length; i++) { // for all words in the phrase ...
            if (!cr.inVocabulary(words[i])) { // ... that are not in the vocabulary ...
                // ... we look for the word in the vocabulary that has the highest probability
                highestProb = Integer.MIN_VALUE;
                String correction = ""; // default to empty strings
                // get all the candidate words for this word along with their probabilities
                Map<String,Double> candidates = getCandidateWords(words[i]);
                // for all candidate words check the probabilities of their bigrams
                for (String canWord : candidates.keySet()) {
                    double prob = Math.log(candidates.get(canWord)); // probability for the correction
                    double bigramprob = 1; // probability for the bigrams
                    if (i != 0) {
                        // if not the first word, evaluate bigram with word in front
                        bigramprob *= Math.log(cr.getSmoothedCount(words[i - 1] + " " 
                                + canWord));
                    }
                    if (i != (words.length - 1)) {
                        // if not the last word, evaluate bigram with word after
                        bigramprob *= Math.log(cr.getSmoothedCount(canWord + " " +
                                words[i + 1]));
                    }
                    // set the final probability as a linear combination
                    prob = .5 * prob + 1 * bigramprob;
                    
                    if (prob > highestProb) {
                        // this is the best candidate so far
                        highestProb = prob;
                        correction = canWord;
                    }
                }
                if (correction.equals("")) {
                    throw new IllegalStateException("No suitable candidate");
                }
                
                // replace the faulty word by the most suitable candidate
                words[i] = correction;
                
                correctedWords++; // we have corrected yet another word
                
                if (correctedWords == 2) {
                    // if we have already corrected two words, stop here, we are done
                    finalSuggestion = String.join(" ", words);
                    return finalSuggestion.trim();
                }
            }
        }
        
        // get all combinations of words that contain an error
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
        for (String[] canSen : candidateSentences.keySet()) { // evaluate each candidate
            double prob;
            prob = Math.log(evaluateBigramSentence(canSen)); 
            // use the noisy channel probabilities for the corrected words
            for (double noisyProb: candidateSentences.get(canSen)) { 
                if (Double.compare(noisyProb, 1.0) != 0) { // corrected word
                    prob += Math.log(noisyProb);
                }
            }
            if (prob > highestProb) { // new best candidate
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
        for (int i = 0; i < words.length; i++) {
            //prob bigram with word in front
            if (i != 0) {
                prob = prob + Math.log(cr.getSmoothedCount(words[i - 1] + " " 
                        + words[i]));
            }
            //prob bigram with word afterwards
            if (i != (words.length - 1)) {
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
     * @param words original sentence
     */
    public void getCandidateSentence(boolean[] errorCombination, Map sentences, String[] words) {
        double[] probabilities = new double[words.length]; // noisy channel probabilities per word
        String[] newSentence = words.clone();
        for (int i = 0; i < words.length; i++) {
            if (errorCombination[i] == true) { // assume word is wrong
                double prob;
                double highestProb = Integer.MIN_VALUE;
                String finalCandidate = "";
                Map<String,Double> candidates = getCandidateWords(words[i]);
                // loop over all candidate words and determine the best option
                for (String canWord : candidates.keySet()) {
                    prob = 0;
                    // probability for the bigram with word in front
                    if (i != 0) {
                        prob = prob + Math.log(cr.getSmoothedCount(words[i - 1] + " " 
                                + canWord));
                    }
                    // probability for the bigram with word afterwords
                    if (i != (words.length - 1)) {
                        prob = prob + Math.log(cr.getSmoothedCount(canWord + " " +
                                words[i + 1]));
                    }
                    prob += Math.log(candidates.get(canWord)); // noisy channel prob
                    if (prob > highestProb) { // found new best candidate
                        highestProb = prob;
                        finalCandidate = canWord;
                        probabilities[i] = prob;
                    }
                }
                newSentence[i] = finalCandidate; // correct sentence
                if (finalCandidate.equals("")) {
                    throw new IllegalStateException("No suitable candidate");
                }
            } else { // word assumed correct
                probabilities[i] = 1.0;
            }
        }
        sentences.put(newSentence, probabilities); // add sentence to map
    }
    
    /**
     * Generates all combinations of positions where faulty words can be located
     * given the correctedWords and the phrase-length constraints.
     * 
     * This method pays attention to the words that have already been corrected as well as to the 
     * constraints that there are never two consecutive errors and that the maximum amount of errors
     * is 2.
     * 
     * @param correctedWords number of words that are already marked as (scheduled to be) corrected
     * @param length length of the phrase
     * @param initialPhrase phrase to start-off with
     * @param maximumCorrections the maximum number of corrections that are allowed
     * @return 
     */
    public HashSet<boolean[]> getErrorCombinations(int correctedWords, 
            int length, String initialPhrase, int maximumCorrections) {
        
        // get corrections that were done by correction of words that are not in the vocabulary
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
    
    /**
     * Looks for words that are in the vocabulary but may still be wrong. Returns combinations of 
     * positions that may contain an error.
     * 
     * This method pays attention to the constraints that there are never two consecutive errors and
     * that the maximum amount of errors is 2.
     * 
     * @param correctedWords number of words that are already marked as (scheduled to be) corrected
     * @param length length of the phrase
     * @param corrections boolean array of length length where corrections[l] means that he word at 
     *  position l is already (scheduled to be) corrected
     * @param maximumCorrections the maximum number of corrections that are allowed
     * @return array of boolean arrays which indicate at which positions errors may exist
     */
    public HashSet<boolean[]> recursiveErrorCombinations(int correctedWords, 
            int length, boolean[] corrections, int maximumCorrections) {
        if (correctedWords == maximumCorrections) { // leaf, no more corrections allowed
            HashSet<boolean[]> list = new HashSet<>();
            list.add(corrections);
            return list;
        } else { // add one correction
            HashSet<boolean[]> list = new HashSet<>();
            list.add(corrections);
            for (int word = 0; word < length; word++) { // for all words
                // check if this word as well as the word before and after have
                // not been corrected
                if (corrections[word] == false 
                        && corrections[Math.max(0, word - 1)] == false
                        && corrections[Math.min(length - 1, word + 1)] == false) 
                {
                    boolean[] newCorrections = corrections.clone();
                    newCorrections[word] = true;
                    // add this correction to list
                    list.add(newCorrections);
                    // add all corrections based on this to the list recursively
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
        for (int i = 0; i <= word.length(); i++) { // for all characters in word
            for (char c : ALPHABET) { // for all characters in alphabet
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
        
        for (int i = 0; i < word.length(); i++) { // for all characters in word
            // fix by substitution
            for (char c : ALPHABET) { // for all characters in alphabet
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
        for (int i = 0; i < word.length() - 1; i++) { // for all characters in word
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