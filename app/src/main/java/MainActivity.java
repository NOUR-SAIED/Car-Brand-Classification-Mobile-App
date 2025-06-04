package com.example.classifyapp;

import androidx.annotation.Nullable; // Utilisé pour indiquer qu'un paramètre ou une valeur de retour peut être null.
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest; // Nécessaire pour demander des permissions (comme CAMERA).
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor; // Permet d'accéder aux fichiers bruts dans le dossier 'assets'.
import android.graphics.Bitmap; // Représente une image bitmap (utilisée pour les images).
import android.media.ThumbnailUtils; // Fournit des utilitaires pour créer des miniatures d'images.
import android.os.Bundle; // Utilisé pour passer des données entre les états d'une activité
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;


import org.tensorflow.lite.Interpreter;


import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity {

    // Déclaration des variables pour les éléments de l'interface utilisateur (UI).
    TextView result, confidence; // Vues texte pour afficher le résultat principal et les confiances détaillées.
    ImageView imageView; // Vue pour afficher l'image prise ou sélectionnée.
    Button picture; // Bouton pour déclencher la prise de photo.

    // Définit la taille (largeur et hauteur) à laquelle l'image sera redimensionnée avant d'être envoyée au modèle.
    // Cette taille DOIT correspondre à la taille d'entrée attendue par le modèle TFLite.
    int imageSize = 224;

    // Déclaration de l'objet Interpreter de TensorFlow Lite. C'est lui qui chargera et exécutera le modèle.
    private Interpreter tflite;

    // Méthode appelée lorsque l'activité est créée pour la première fois.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Appelle la méthode onCreate de la classe parente (AppCompatActivity).
        super.onCreate(savedInstanceState);
        // Définit le layout (l'interface utilisateur) à afficher pour cette activité, à partir du fichier activity_main.xml.
        setContentView(R.layout.activity_main);

        // Lie les variables Java aux éléments UI définis dans le fichier XML layout, en utilisant leurs IDs.
        result = findViewById(R.id.result);
        confidence = findViewById(R.id.confidence);
        imageView = findViewById(R.id.imageView);
        picture = findViewById(R.id.button); // Lie la variable 'picture' au bouton avec l'ID 'button'.

        // Tentative de chargement du modèle TensorFlow Lite au démarrage de l'application.
        try {
            // Appelle la méthode helper 'loadModelFile' pour obtenir le modèle sous forme de ByteBuffer.
            // Crée une nouvelle instance de l'Interpreter TFLite avec le modèle chargé.
            tflite = new Interpreter(loadModelFile());
            // Affiche un message de succès dans le Logcat si le chargement réussit
            Log.i("ModelLoad", "Modèle TFLite chargé avec succès.");
        } catch (IOException e) {
            // Si une erreur (IOException) se produit pendant le chargement ,
            // affiche la trace de l'erreur dans le Logcat pour le débogage.
            e.printStackTrace();
            // Affiche un message d'erreur dans le Logcat .
            Log.e("ModelLoad", "Erreur lors du chargement du modèle TFLite", e);

        }

        // Définit un écouteur d'événements (ClickListener) pour le bouton 'picture'.
        // Le code à l'intérieur de 'onClick' sera exécuté lorsque l'utilisateur appuiera sur le bouton.
        picture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Vérifie si l'application a la permission d'utiliser l'appareil photo.
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    // Si la permission est accordée :
                    // Crée une intention (Intent) pour demander au système de lancer une application appareil photo.
                    Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    // Démarre l'activité appareil photo. Le '1' est un code de requête pour identifier
                    // le résultat de cette activité spécifique dans onActivityResult.
                    startActivityForResult(cameraIntent, 1);
                } else {
                    // Si la permission n'est PAS accordée :
                    // Demande la permission CAMERA à l'utilisateur.
                    // Le '100' est un code de requête pour identifier la réponse à cette demande de permission.
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
                }
            }
        });

        // TODO: Ajouter ici l'initialisation et le ClickListener pour le bouton "Select Image from Gallery"
        // Button galleryButton = findViewById(R.id.buttonGallery);
        // galleryButton.setOnClickListener( ... );

    } // Fin de la méthode onCreate

    // Méthode privée helper pour charger le fichier modèle depuis le dossier 'assets'.
    // Renvoie les données du modèle sous forme de ByteBuffer.
    // 'throws IOException' indique que cette méthode peut lever une exception si le fichier n'est pas trouvé ou lisible.
    private ByteBuffer loadModelFile() throws IOException {
        // Ouvre le fichier modèle spécifié ("model.tflite") depuis le dossier 'assets'.

        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("model.tflite");
        // Crée un flux d'entrée pour lire le fichier.
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        // Obtient un canal de fichier pour une lecture efficace.
        FileChannel fileChannel = inputStream.getChannel();
        // Obtient le décalage de début et la longueur déclarée du fichier dans l'archive assets.
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        // Mappe directement le contenu du fichier en mémoire dans un ByteBuffer. C'est efficace pour les gros fichiers.
        // Le mode READ_ONLY indique qu'on ne modifiera pas le modèle en mémoire.
        // NOTE: fileChannel.map() retourne un MappedByteBuffer, une sous-classe de ByteBuffer.
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    // Méthode principale pour effectuer la classification d'une image.
    // Prend une image Bitmap en entrée.
    // @SuppressLint("DefaultLocale") supprime un avertissement lié au formatage de chaînes potentiellement dépendant de la locale.
    @SuppressLint("DefaultLocale")
    public void classifyImage(Bitmap image) {
        // Vérifie si le modèle a été correctement chargé.
        if (tflite == null) {
            Log.e("Classification", "Interprète TFLite non initialisé.");
            // TODO: Informer l'utilisateur.
            return; // Ne rien faire si le modèle n'est pas prêt.
        }
        // Vérifie si l'image fournie n'est pas null.
        if (image == null) {
            Log.e("Classification", "Image d'entrée est null.");
            // TODO: Informer l'utilisateur.
            return;
        }

        // Bloc try-catch pour gérer les erreurs potentielles pendant la classification.
        try {
            // ** Pré-traitement de l'image **

            // Affiche les dimensions de l'image *avant* tout redimensionnement dans cette méthode.
            // Note : L'image passée ici DOIT DÉJÀ être redimensionnée à imageSize x imageSize par onActivityResult.
            Log.d("ImageCheck", "Dimensions de l'image reçue pour classification : " + image.getWidth() + "x" + image.getHeight());

            // Crée un ByteBuffer pour contenir les données de l'image à envoyer au modèle.
            // allocateDirect est généralement plus performant pour l'interaction avec les bibliothèques natives comme TFLite.
            // Taille = 4 (octets par float) * largeur * hauteur * 3 (canaux RGB)
            // NOTE: Ceci suppose un modèle FLOAT32. Si votre modèle est QUANTIZED (UINT8), utilisez 1 au lieu de 4.
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            // Définit l'ordre des octets (endianness) pour correspondre à celui du matériel natif.
            byteBuffer.order(ByteOrder.nativeOrder());
            // Remet la position du buffer à 0 avant d'écrire dedans.
            byteBuffer.rewind();

            // Crée un tableau pour stocker les valeurs entières des pixels de l'image.
            int[] intValues = new int[imageSize * imageSize];
            // Extrait les données de couleur des pixels du Bitmap dans le tableau intValues.
            image.getPixels(intValues, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());

            // Boucle pour parcourir chaque pixel de l'image (redimensionnée).
            int pixel = 0;
            for (int i = 0; i < imageSize; i++) {
                for (int j = 0; j < imageSize; j++) {
                    // Obtient la valeur entière ARGB du pixel courant.
                    int val = intValues[pixel++];
                    // Extrait les composantes Rouge, Vert, Bleu (ignore Alpha).
                    // Décale les bits vers la droite pour isoler chaque composante (8 bits).
                    // Applique un masque '& 0xFF' pour obtenir uniquement les 8 bits de la couleur.
                    // Normalise la valeur de chaque canal de couleur en la divisant par 255.0 pour obtenir une valeur flottante entre 0.0 et 1.0.
                    // Cette normalisation est courante, mais dépend de la façon dont le modèle a été entraîné.
                    // Ajoute les valeurs flottantes normalisées au ByteBuffer.
                    // NOTE: Si votre modèle est QUANTIZED (UINT8), vous mettriez directement les octets R, G, B sans normaliser :
                    // byteBuffer.put((byte) ((val >> 16) & 0xFF)); // R
                    // byteBuffer.put((byte) ((val >> 8) & 0xFF));  // G
                    // byteBuffer.put((byte) (val & 0xFF));         // B
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255.f)); // R
                    byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255.f));  // G
                    byteBuffer.putFloat((val & 0xFF) * (1.f / 255.f));         // B
                }
            }

            // ** Exécution de l'inférence (classification) **

            // Crée un tableau pour stocker les résultats (sorties) du modèle.
            // La structure est [batchSize][numberOfClasses]. Ici, batchSize=1, numberOfClasses=3.
            // NOTE : Le commentaire "// Matches your 2 classes" est incorrect, la taille est 3.
            // NOTE : Ceci suppose une sortie FLOAT32. Si la sortie est QUANTIZED (UINT8), utilisez byte[][] output = new byte[1][3];
            float[][] output = new float[1][3];

            // Exécute le modèle TFLite.
            // Prend le ByteBuffer d'entrée préparé (byteBuffer) et remplit le tableau de sortie (output).
            tflite.run(byteBuffer, output);

            // ** Post-traitement des résultats **

            // Affiche les valeurs brutes de sortie du modèle dans le Logcat pour le débogage.
            Log.d("Classification", "Sorties brutes du modèle: " + Arrays.toString(output[0]));

            // Définit les noms des classes possibles. L'ordre DOIT correspondre à l'ordre des sorties du modèle.
            String[] classes = {"Audi", "Rolls Royce", "Toyota Inova"};

            // Recherche l'index (la position) de la classe ayant la plus haute confiance.
            int maxPos = 0; // Index de la classe avec la plus haute confiance.
            float maxConfidence = output[0][0]; // Confiance de la classe ayant la plus haute confiance. Initialise avec la première classe.

            // Boucle à travers les scores de confiance pour les classes restantes (à partir de l'index 1).
            for (int i = 1; i < output[0].length; i++) {
                // Si la confiance de la classe actuelle est supérieure à la confiance maximale trouvée jusqu'à présent...
                if (output[0][i] > maxConfidence) {
                    // Met à jour la confiance maximale.
                    maxConfidence = output[0][i];
                    // Met à jour l'index de la classe ayant la confiance maximale.
                    maxPos = i;
                }
                // Affiche la confiance pour chaque classe (utile pour le débogage).
                Log.d("Classification", classes[i] + " confiance: " + output[0][i]);
            }

            // ** Mise à jour de l'Interface Utilisateur (UI) **

            // Crée des variables finales pour les utiliser dans la lambda expression de runOnUiThread.
            int finalMaxPos = maxPos;
            // Assure que le code qui modifie l'interface utilisateur s'exécute sur le thread UI principal d'Android.
            runOnUiThread(() -> {
                // Définit le texte du TextView 'result' avec le nom de la classe prédite (celle avec la plus haute confiance).
                result.setText(classes[finalMaxPos]);

                // Construit une chaîne de caractères pour afficher les confiances détaillées de toutes les classes.
                StringBuilder s = new StringBuilder();
                for (int i = 0; i < classes.length; i++) {
                    // Ajoute une ligne pour chaque classe : "NomClasse: XX.X%"
                    // Multiplie la confiance (qui est entre 0.0 et 1.0) par 100 pour obtenir un pourcentage.
                    s.append(String.format("%s: %.1f%%\n", classes[i], output[0][i] * 100));
                    // NOTE: Si la sortie était UINT8, il faudrait d'abord la déquantifier avant de calculer le pourcentage.
                }
                // Définit le texte du TextView 'confidence' avec la chaîne construite.
                // .trim() supprime les espaces ou sauts de ligne en trop à la fin.
                confidence.setText(s.toString().trim());
            });

            // Gère les exceptions qui pourraient survenir pendant la classification.
        } catch (Exception e) {
            // Affiche l'erreur dans le Logcat.
            Log.e("Classification", "Erreur pendant la classification", e);
            // Met à jour l'UI (sur le thread principal) pour indiquer à l'utilisateur qu'une erreur s'est produite.
            runOnUiThread(() -> {
                result.setText("Erreur"); // Indique une erreur dans le résultat principal.
                confidence.setText("La classification a échoué: " + e.getMessage()); // Affiche le message d'erreur.
            });
        }
    } // Fin de la méthode classifyImage

    // Méthode appelée lorsque l'activité est sur le point d'être détruite.
    @Override
    protected void onDestroy() {
        // Appelle la méthode onDestroy de la classe parente.
        super.onDestroy();
        // Vérifie si l'objet Interpreter TFLite a été initialisé.
        if (tflite != null) {
            // Libère les ressources natives utilisées par l'Interpreter TFLite.
            // C'est TRÈS IMPORTANT pour éviter les fuites de mémoire.
            tflite.close();
            tflite = null; // Aide le garbage collector.
            Log.i("ModelLoad", "Interprète TFLite fermé.");
        }
    }

    // Méthode appelée lorsqu'une activité démarrée avec startActivityForResult (comme l'appareil photo) se termine.
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        // Vérifie si le résultat provient de notre requête appareil photo (requestCode == 1)
        // ET si l'opération a réussi (resultCode == RESULT_OK).
        if (requestCode == 1 && resultCode == RESULT_OK) {
            // Vérifie si des données ont été retournées (l'intent 'data' ne doit pas être null).
            if (data != null && data.getExtras() != null) {
                // Récupère l'image capturée depuis les "extras" de l'intent.
                // Note : Souvent, ce n'est qu'une miniature (thumbnail) de l'image réelle.
                Bitmap image = (Bitmap) data.getExtras().get("data");

                // Si l'image a bien été récupérée :
                if (image != null) {
                    // Crée une miniature carrée à partir de l'image reçue (qui pourrait être rectangulaire).
                    int dimension = Math.min(image.getWidth(), image.getHeight());
                    Bitmap thumbnail = ThumbnailUtils.extractThumbnail(image, dimension, dimension);

                    // Affiche la miniature carrée dans l'ImageView.
                    imageView.setImageBitmap(thumbnail);

                    // Redimensionne la miniature (ou l'image originale si elle était déjà carrée)
                    // à la taille exacte attendue par le modèle (imageSize x imageSize).
                    // Le dernier paramètre 'false' concerne le filtrage (true est souvent mieux pour la qualité).
                    Bitmap resizedImage = Bitmap.createScaledBitmap(thumbnail, imageSize, imageSize, false);

                    // Appelle la méthode de classification avec l'image redimensionnée.
                    classifyImage(resizedImage);
                } else {
                    Log.w("ActivityResult", "Bitmap reçu de la caméra est null.");

                }
            } else {
                Log.w("ActivityResult", "Intent data ou extras de la caméra sont null.");

            }
        }
        // TODO: Ajouter une clause 'else if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK)'
        // pour gérer le résultat de la sélection depuis la galerie.

        // Appelle la méthode onActivityResult de la classe parente pour gérer d'autres cas si nécessaire.
        super.onActivityResult(requestCode, resultCode, data);
    } // Fin de onActivityResult

    // TODO: Implémenter la méthode onRequestPermissionsResult pour gérer la réponse de l'utilisateur
    // à la demande de permission CAMERA.

} // Fin de la classe MainActivity