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
                    double prob = candidates.get(canWord);
                    if(i != 0) {
                        prob = prob * cr.getSmoothedCount(words[i - 1] + words[i]);
                    }
                    if(i != (words.length - 1)) {
                        prob = prob * cr.getSmoothedCount(words[i] + words[i + 1]);
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
        
        
        getCandidateWords("tha");
        
        
        
        return finalSuggestion.trim();
    }    
      
    /** returns a map with candidate words and their noisy channel probability. **/
    public Map<String,Double> getCandidateWords(String word)
    {
        Map<String,Double> mapOfWords = new HashMap<>();
        
        Set<String> candidateWords = new HashSet();
        String newWord;
        
        for(int i = 0; i <= word.length(); i++) {
            for(char c : ALPHABET) {
                //insertions
                newWord = word.substring(0, i) + c;
                newWord = newWord + word.substring(i, word.length());
                if (this.cr.inVocabulary(newWord) ) {
                    Double confusion1 = 0.0;
                    Double confusion2 = 0.0;
                    if (i - 1 > 0) {
                        confusion1 = (double) this.cmr.getConfusionCount(
                            String.valueOf(word.charAt(i - 1)), 
                            String.valueOf(word.charAt(i - 1)) + c
                        );
                    }
                    if (i < word.length()) {
                        confusion2 = (double) this.cmr.getConfusionCount(
                            String.valueOf(word.charAt(i)),
                            c + String.valueOf(word.charAt(i))
                        );
                    }
                    mapOfWords.put(newWord, confusion1 + confusion2);
                }
            }
        }
        
        for(int i = 0; i < word.length(); i++) {
            for(char c : ALPHABET) {
                //replacements
                newWord = word.substring(0, i) + c;
                candidateWords.add(newWord + word.substring(i + 1, word.length()));
            }
            
            //deletions
            newWord = word.substring(0, i);
            newWord = newWord + word.substring(i + 1, word.length());
            candidateWords.add(newWord);
        }
              
        //transpositions
        for(int i = 0; i < word.length() - 1; i++) { 
            newWord = word.substring(0, i) + word.charAt(i + 1) + word.charAt(i)
                    + word.substring(i+2, word.length());
            //System.out.println(newWord);
            candidateWords.add(newWord);
        }       


        for(String s: candidateWords) {
            System.out.println(s);
        }
        
        
        
        return mapOfWords;
    }            
}