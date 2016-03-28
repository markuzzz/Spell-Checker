

import java.io.IOException;
import java.util.Scanner;


public class SpellChecker {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) 
    {
        boolean inPeach = false; // set this to true if you submit to peach!!!
        
        try {
            CorpusReader cr = new CorpusReader();
            ConfusionMatrixReader cmr = new ConfusionMatrixReader();
            SpellCorrector sc = new SpellCorrector(cr, cmr);
            if (inPeach) {
                peachTest(sc);
            } else {
                nonPeachTest(sc);
            }
        } catch (Exception ex) {
            System.out.println(ex);
            ex.printStackTrace();
        }
    }
    
    static void nonPeachTest(SpellCorrector sc) throws IOException { 
            String[] sentences = {
                "this assay allowed us to measure a wide variety of conditions",
                "this assay allowed us to measure a wide variety of conitions",
                "this assay allowed us to meassure a wide variety of conditions",
                "this assay allowed us to measure a wide vareity of conditions",
                "at the home locations there were traces of water",
                "at the hme locations there were traces of water",
                "at the hoome locations there were traces of water",
                "at the home locasions there were traces of water",
                "the development of diabetes is present in mice that carry a transgen",
                "the development of diabetes is present in moce that carry a transgen",
                "the development of idabetes is present in mice that carry a transgen",
                "the development of diabetes us present in mice that harry a transgen",
                "laying in the national football league was my dream",
                "this advise is taking into consideration the fact that the govenrment bans political parties",
                "essentially here has bien no change in japan",
                "a responses may be any measurable biological parametre that is correlated with the toxicant",
                "she still refers too me as a friend but i feel i am treated quite batly",
                "she still refers to me has a friend but i fel i am treated quite badly",
                "boing gloves shield the knuckles nut the head"
            };
            
            String[] reference = {
                "this assay allowed us to measure a wide variety of conditions",
                "this assay allowed us to measure a wide variety of conditions",
                "this assay allowed us to measure a wide variety of conditions",
                "this assay allowed us to measure a wide variety of conditions",
                "at the home locations there were traces of water",
                "at the home locations there were traces of water",
                "at the home locations there were traces of water",
                "at the home locations there were traces of water",
                "the development of diabetes is present in mice that carry a transgene",
                "the development of diabetes is present in mice that carry a transgene",
                "the development of diabetes is present in mice that carry a transgene",
                "the development of diabetes is present in mice that carry a transgene",
                "playing in the national football league was my dream",
                "this advice is taking into consideration the fact that the government bans political parties",
                "essentially there has been no change in japan",
                "a response may be any measurable biological parameter that is correlated with the toxicant",
                "she still refers to me as a friend but i feel i am treated quite badly",
                "she still refers to me as a friend but i feel i am treated quite badly",
                "boxing gloves shield the knuckles not the head"
            };
            
            int grade = 0;
            
            for(int i = 0; i < sentences.length; i++) {
                String s0 = sentences[i];
                System.out.println("Input : " + s0);
                String result=sc.correctPhrase(s0);
                System.out.println("Answer: " +result);
                if (result.equals(reference[i])) {
                    grade++;
                }
                System.out.println();
            }
            System.out.println("Grade: " + grade + "/" + reference.length);
    }
    
    static void peachTest(SpellCorrector sc) throws IOException {
            Scanner input = new Scanner(System.in);
            
            String sentence = input.nextLine();
            System.out.println("Answer: " + sc.correctPhrase(sentence));  
    } 
}