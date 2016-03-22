import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
        String finalSuggestion = "";
        double highestProb;
        
        for(int i = 0; i < words.length; i++) {
            if(!cr.inVocabulary(words[i])) {
                highestProb = 0;
                String correction = "";
                Map<String,Double> candidates = getCandidateWords(words[i]);
                for(String canWord : candidates.keySet()) {
                    double prob = Math.log(candidates.get(canWord));
                    if(i != 0) {
                        prob = prob * Math.log(cr.getSmoothedCount(words[i - 1] + " " 
                                + canWord));
                    }
                    if(i != (words.length - 1)) {
                        prob = prob * Math.log(cr.getSmoothedCount(canWord + " " +
                                words[i + 1]));
                    }
                    if(prob > highestProb) {
                        highestProb = prob;
                        correction = canWord;
                    }
                    
                }
                if (correction == "") {
                    throw new IllegalStateException("no suitable candidate");
                }
                words[i] = correction;
                correctedWords++;
            }
        }
        
        
        
        return finalSuggestion.trim();
    }    
      
    /** returns a map with candidate words and their noisy channel probability. **/
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
        
        for(int i = 0; i <= word.length(); i++) {
            for(char c : ALPHABET) {
                // fix by insertion
                newWord = word.substring(0, i) + c;
                newWord = newWord + word.substring(i, word.length());
                if (this.cr.inVocabulary(newWord)) {
                    String letterInFront = " ";
                    if (i - 1 > 0) {
                        letterInFront = String.valueOf(word.charAt(i - 1));
                    }
                    confusion = this.cmr.getConfusionCount(
                        letterInFront, 
                        letterInFront + c
                    );
                    count = this.cmr.getCharCount(letterInFront + c);
                    probability = (double) confusion / (double) count;
                    mapOfWords.put(newWord, probability);
                }
            }
        }
        
        for(int i = 0; i < word.length(); i++) {
            for(char c : ALPHABET) {
                //replacements
                newWord = word.substring(0, i) + c;
                newWord = newWord + word.substring(i + 1, word.length());
                if (this.cr.inVocabulary(newWord)) {
                    confusion = this.cmr.getConfusionCount(
                            String.valueOf(word.charAt(i)), String.valueOf(c)
                    );
                    count = this.cmr.getCharCount(String.valueOf(c));
                    probability = (double) confusion / (double) count;
                    mapOfWords.put(newWord, probability);
                }
            }
            
            // fix by deletion
            newWord = word.substring(0, i);
            newWord = newWord + word.substring(i + 1, word.length());
            if (this.cr.inVocabulary(newWord)) {
                confusion = this.cmr.getConfusionCount(
                        " " + String.valueOf(word.charAt(i)), " "
                );
                count = this.cmr.getCharCount(" ");
                probability = (double) confusion / (double) count;
                mapOfWords.put(newWord, probability);
            }
        }
              
        //transpositions
        for(int i = 0; i < word.length() - 1; i++) { 
            newWord = word.substring(0, i) + word.charAt(i + 1) + word.charAt(i)
                    + word.substring(i+2, word.length());
            if (this.cr.inVocabulary(newWord)) {
                confusion = this.cmr.getConfusionCount(
                        word.substring(i, i + 2), 
                        String.valueOf(word.charAt(i + 1)) + 
                                String.valueOf(word.charAt(i))
                );
                count = this.cmr.getCharCount(word.substring(i, i + 2));
                probability = (double) confusion / (double) count;
                mapOfWords.put(newWord, probability);
            }
        }       

        return mapOfWords;
    }            
}