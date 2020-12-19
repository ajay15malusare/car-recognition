package com.example.carrecognition;

import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class Classify extends AppCompatActivity {

    // presets for rgb conversion
    private static final int RESULTS_TO_SHOW = 3;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;

    // options for model interpreter
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();
    // tflite graph
    private Interpreter tflite;
    // holds all the possible labels for model
    private List<String> labelList;
    // holds the selected image data as bytes
    private ByteBuffer imgData = null;
    // holds the probabilities of each label for non-quantized graphs
    private float[][] labelProbArray = null;
    // holds the probabilities of each label for quantized graphs
    private byte[][] labelProbArrayB = null;
    // array that holds the labels with the highest probabilities
    private String[] topLables = null;
    // array that holds the highest probabilities
    private String[] topConfidence = null;


    // selected classifier information received from extras
    private String chosen;
    private boolean quant;

    // input image dimensions for the Inception Model
    private int DIM_IMG_SIZE_X = 224;
    private int DIM_IMG_SIZE_Y = 224;
    private int DIM_PIXEL_SIZE = 3;

    // int array to hold image data
    private int[] intValues;

    // activity elements
    private ImageView selected_image;
    private Button classify_button;
    private ImageView back_button;
    private TextView label1;
    private TextView label2;
    private TextView label3;
    private TextView Confidence1;
    private TextView Confidence2;
    private TextView Confidence3,con_text,time_text,loc_text,price_txt,m_txt;
    Animation fromtopbottom, fromtopbottomtwo, fromtopbottomthree;
    LinearLayout itemOne;

    // priority queue that will hold the top results from the CNN
    private PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    new Comparator<Map.Entry<String, Float>>() {
                        @Override
                        public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
                            return (o1.getValue()).compareTo(o2.getValue());
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // get all selected classifier data from classifiers
        chosen = (String) getIntent().getStringExtra("chosen");
        quant = (boolean) getIntent().getBooleanExtra("quant", false);

        // initialize array that holds image data
        intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];

        super.onCreate(savedInstanceState);

        //initilize graph and labels
        try{
            tflite = new Interpreter(loadModelFile(), tfliteOptions);
            labelList = loadLabelList();
        } catch (Exception ex){
            ex.printStackTrace();
        }

        // initialize byte array. The size depends if the input data needs to be quantized or not
        if(quant){
            imgData =
                    ByteBuffer.allocateDirect(
                            DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
        } else {
            imgData =
                    ByteBuffer.allocateDirect(
                            4 * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
        }
        imgData.order(ByteOrder.nativeOrder());

        // initialize probabilities array. The datatypes that array holds depends if the input data needs to be quantized or not
        if(quant){
            labelProbArrayB= new byte[1][labelList.size()];
        } else {
            labelProbArray = new float[1][labelList.size()];
        }

        setContentView(R.layout.activity_desc);

        fromtopbottom = AnimationUtils.loadAnimation(this, R.anim.fromtopbottom);
        fromtopbottomtwo = AnimationUtils.loadAnimation(this, R.anim.fromtopbottomtwo);
        fromtopbottomthree = AnimationUtils.loadAnimation(this, R.anim.fromtopbottomthree);
        itemOne = (LinearLayout) findViewById(R.id.itemOne);
        itemOne.startAnimation(fromtopbottom);



        // labels that hold top three results of CNN
        label1 = (TextView) findViewById(R.id.label1);
        con_text = (TextView) findViewById(R.id.con_text);
        loc_text = (TextView) findViewById(R.id.loc_text);
        m_txt=(TextView)findViewById(R.id.m_txt);

        price_txt = (TextView) findViewById(R.id.price_txt);
        time_text = (TextView) findViewById(R.id.time_text);



        // initialize imageView that displays selected image to the user
        selected_image = (ImageView) findViewById(R.id.selected_image);

        // initialize array to hold top labels
        topLables = new String[RESULTS_TO_SHOW];
        // initialize array to hold top probabilities
        topConfidence = new String[RESULTS_TO_SHOW];

        // allows user to go back to activity to select a different image
        back_button = (ImageView)findViewById(R.id.back_button);
        back_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(Classify.this, ChooseModel.class);
                startActivity(i);
            }
        });



        // get image from previous activity to show in the imageView
        Uri uri = (Uri)getIntent().getParcelableExtra("resID_uri");
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            selected_image.setImageBitmap(bitmap);
            // not sure why this happens, but without this the image appears on its side
            selected_image.setRotation(selected_image.getRotation() + 90);
            Bitmap bitmap_orig = ((BitmapDrawable)selected_image.getDrawable()).getBitmap();
            // resize the bitmap to the required input size to the CNN
            Bitmap bitmapp = getResizedBitmap(bitmap_orig, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y);
            // convert bitmap to byte array
            convertBitmapToByteBuffer(bitmapp);
            // pass byte data to the graph
            if(quant){
                tflite.run(imgData, labelProbArrayB);
            } else {
                tflite.run(imgData, labelProbArray);
            }
            // display the results
            printTopKLabels();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // loads tflite grapg from file
    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd(chosen);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    // converts bitmap to byte array which is passed in the tflite graph
    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        if (imgData == null) {
            return;
        }
        imgData.rewind();
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        // loop through all pixels
        int pixel = 0;
        for (int i = 0; i < DIM_IMG_SIZE_X; ++i) {
            for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) {
                final int val = intValues[pixel++];
                // get rgb values from intValues where each int holds the rgb values for a pixel.
                // if quantized, convert each rgb value to a byte, otherwise to a float
                if(quant){
                    imgData.put((byte) ((val >> 16) & 0xFF));
                    imgData.put((byte) ((val >> 8) & 0xFF));
                    imgData.put((byte) (val & 0xFF));
                } else {
                    imgData.putFloat((((val >> 16) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                    imgData.putFloat((((val >> 8) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                    imgData.putFloat((((val) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                }

            }
        }
    }

    // loads the labels from the label txt file in assets into a string array
    private List<String> loadLabelList() throws IOException {
        List<String> labelList = new ArrayList<String>();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(this.getAssets().open("labels.txt")));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    // print the top labels and respective confidences
    private void printTopKLabels() {
        // add all results to priority queue
        for (int i = 0; i < labelList.size(); ++i) {
            if(quant){
                sortedLabels.add(
                        new AbstractMap.SimpleEntry<>(labelList.get(i), (labelProbArrayB[0][i] & 0xff) / 255.0f));
            } else {
                sortedLabels.add(
                        new AbstractMap.SimpleEntry<>(labelList.get(i), labelProbArray[0][i]));
            }
            if (sortedLabels.size() > RESULTS_TO_SHOW) {
                sortedLabels.poll();
            }
        }

        // get top results from priority queue
        final int size = sortedLabels.size();
        for (int i = 0; i < size; ++i) {
            Map.Entry<String, Float> label = sortedLabels.poll();
            topLables[i] = label.getKey();
            topConfidence[i] = String.format("%.0f%%",label.getValue()*100);
        }

        //getApplicationContext(),topLables[2], Toast.LENGTH_LONG).show();

        // set the corresponding textviews with the results

        //Confidence1.setText(topConfidence[2]);
        String toplbl = topLables[2];

        if (toplbl.equals("0 santro")) {
            selected_image.setImageDrawable(getResources().getDrawable(R.drawable.santro));
            selected_image.setRotation(selected_image.getRotation() +  -90);
            label1.setText("Santro");
            loc_text.setText("Engine:- 1.1 Epsilon Mpi (BS6)");
            time_text.setText("No. of Seats:-5");
            m_txt.setText("Mileage:- 30.48 km/kg");
            price_txt.setText("Price: Rs.4.57 - 6.2 Lakh");
            con_text.setText("The new Hyundai Santro is priced in the range of Rs 3.9 lakh to Rs 5.65 lakh (ex-showroom pan-India). Its offered in five variants - D-lite, Era, Magna, Sportz and Asta - two fuel options and two gearbox options. The 5-speed AMT is only available with petrol engine and is limited to the Magna and Sportz variants. If you are eyeing the Santro but confused about which variant fits your budget and serves your requirement the best, this handy guide should help making a decision easier.");

        }
        else if(toplbl.equals("1 renualt")) {
            selected_image.setImageDrawable(getResources().getDrawable(R.drawable.renault));
            selected_image.setRotation(selected_image.getRotation() +  -90);
            label1.setText("Renualt koleos");
            loc_text.setText("Engine:- dCi Diesel Engine");
            time_text.setText("No. of Seats:-5");
            m_txt.setText("Mileage:- 17.15 kmpl");
            price_txt.setText("Price: ₹ 27.49 - 32.47 Lakh");
            con_text.setText("Renault came up with premium models unlike other car makers that eye conquering volume segments. The premium SUV Koleos was one of the first models from Renault to join its product portfolio for domestic market. Despite being a good product the SUV goes unnoticed amid potent rivals that have proven their credibility time and again. The SUV is propelled by the new generation 2.0 litre, dCi diesel powertrain offering power in two states of tune. Transmission choice includes a manual unit available on 2WD and 4WD variants, whereas the auto box is offered only on the 4WD variant. ");


        }
        else if(toplbl.equals("2 swift")){
            selected_image.setImageDrawable(getResources().getDrawable(R.drawable.swift));
            selected_image.setRotation(selected_image.getRotation() +  -90);
            label1.setText("Swift Dzire");
            loc_text.setText("Engine:- 1.2 Litre Petrol Engine");
            time_text.setText("No. of Seats:-5");
            m_txt.setText("Mileage:- 24.12 kmpl");
            price_txt.setText("Price: ₹ 5.89 - 8.8 Lakh");
            con_text.setText("The third generation Maruti Suzuki Swift has been launched in the country with prices starting at Rs 4.99 lakh (ex-showroom, Delhi). The newest iteration of the hatchback is priced up to Rs 8.29 lakh for the range-topping variant. The new generation Maruti Suzuki Swift is available in both petrol and diesel engine options, and for the first time get the option of a diesel automatic as well. We drove the India spec model last month and came back very impressed with how much the car has evolved in its latest avatar. Deliveries for the new Swift will commence later this month, with the car already commanding a waiting period of six to eight weeks.");

        }
        else if(toplbl.equals("3 scorpio")){
            selected_image.setImageDrawable(getResources().getDrawable(R.drawable.scorpio));
            selected_image.setRotation(selected_image.getRotation() +  -90);
            label1.setText("Mahindra Scorpio");
            loc_text.setText("Engine:- 2179 cc Disel Engine");
            time_text.setText("No. of Seats:-7");
            m_txt.setText("Mileage:- 15 kmpl");
            price_txt.setText("Price: ₹ 12.49 - 16.23 Lakh");
            con_text.setText("Mahindra has revealed the specifications of the BS6 Scorpio and has also announced the price of its different variants. Deliveries of the BS6 Scorpio will take place once the coronavirus lockdown is lifted. Mahindra Scorpio Price and Variants: The BS6 Scorpio is available in four variants: S5, S7, S9, and S11. Mahindra will continue to be offer in 7-, 8-, and 9-seating configurations. Mahindra Scorpio Engine: Mahindra is offering the Scorpio with a BS6-compliant 2.2-litre diesel engine that churns out 140PS and 320Nm. While the new S5 base variant is being offered with a 5-speed manual transmission, the S7, S9, and S11 variants wil come mated to a 6-speed MT gearbox.");

        }
        else if(toplbl.equals("4 honda")){
            selected_image.setImageDrawable(getResources().getDrawable(R.drawable.honda));
            selected_image.setRotation(selected_image.getRotation() +  -90);
            label1.setText("Honda Jazz");
            loc_text.setText("Engine:- 1199 cc Petrol Engine");
            time_text.setText("No. of Seats:-5");
            m_txt.setText("Mileage:- 17.1 kmpl");
            price_txt.setText("Price: ₹ 7.68 - 9.92 Lakh");
            con_text.setText("Honda offers the new Jazz only with a 1.2-litre petrol engine that makes 90PS and 110Nm. It is mated to either a 5-speed manual or a 7-step CVT gearbox. Their claimed fuel-efficiency figures are 16.6kmpl and 17.1kmpl respectively. Honda has axed the previously offered 1.5-litre diesel engine (100PS/200Nm). Honda Jazz Features: The updated Jazz gets new features such as LED headlamps and fog lamps as well as a sunroof. It continues to get cruise control, paddle shifters (CVT variants only), a 7-inch touchscreen infotainment system with Android Auto and Apple CarPlay, auto AC, and 15-inch alloy wheels.");

        }
        else if(toplbl.equals("5 audi")){
            selected_image.setImageDrawable(getResources().getDrawable(R.drawable.audi));
            selected_image.setRotation(selected_image.getRotation() +  -90);
            label1.setText("Audi Q3 30");
            loc_text.setText("Engine:- 1395 cc Petrol Engine");
            time_text.setText("No. of Seats:-5");
            m_txt.setText("Mileage:- 16.9 kmpl");
            price_txt.setText("Price: ₹ 34.97 Lakh");
            con_text.setText("Audi Q3 30 TFSI Premium is the top model in the Q3 lineup and the price of Q3 top model is ₹ 34.97 Lakh. This 30 TFSI Premium variant comes with an engine putting out 148 bhp @ 5000 rpm and 250 Nm @ 1500 rpm of max power and max torque respectively. Audi Q3 30 TFSI Premium is available in transmission and offered in 4 colours: Mythos Black, Floret Silver, Misano Red and Cortina White.");

        }
    }


    // resizes bitmap to given dimensions
    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        return resizedBitmap;
    }
}
