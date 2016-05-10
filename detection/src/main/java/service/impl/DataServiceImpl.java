package service.impl;


import dao.PacketDao;
import dto.PacketsInfo;
import dto.Picture;
import dto.StorageByDestinationInTimeDomain;
import dto.ValueInTimeInterval;
import dto.mutualInformation.MutualInformationTable;
import entities.Packet;
import exceptions.GeneralException;
import org.apache.commons.math3.linear.*;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RectangleInsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import service.DataService;

import javax.transaction.Transactional;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

import static java.lang.System.arraycopy;

@Service("dataService")
public class DataServiceImpl implements DataService {

    @Autowired
    private PacketDao packetDao;

    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private DecimalFormat decimalFormat = new DecimalFormat("0.0000");
    private String pictureDirectory = "C:\\Users\\K\\Desktop\\Bakalauras\\latex\\paveiksleliai\\";

    @Override
    @Transactional
    public void uploadFileToDatabase(String[] fileNames) throws GeneralException {
        int id = packetDao.getMaxPacketId() + 1;
        for (int i = 0, j = 1; i < fileNames.length; i++) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(fileNames[i]));
                String line;
                List<Packet> temp = new ArrayList<Packet>();
                while ((line = br.readLine()) != null) {
                    /* Gaunam paketą iš eilutės */
                    Packet p = getPacket(line, i);
                    p.setId(id);
                    temp.add(p);

                    if (j % 1000 == 0) {
                        packetDao.insertPackets(temp);
                        temp = new ArrayList<Packet>();
                    }
                    j++;
                    id++;
                }
                packetDao.insertPackets(temp);
            } catch (Exception e) {
                throw new GeneralException("Could not upload file to the database!", e);
            }
        }
    }

    private Packet getPacket(String line, int fileIndex) throws GeneralException {
        /* Suskaidom eilutę pagal "," */
        String[] cols = line.split("\\\",\\\"");

        /* Nuo pirmo stulpelio pašalinam kabutes*/
        cols[0] = cols[0].substring(1);

        /* Nuo paskutinio stulpelio pašalinam kabutes*/
        cols[cols.length - 1] = cols[cols.length - 1].substring(0, cols[cols.length - 1].length() - 1);

        Packet packet = new Packet();

        // Stulpelių indeksai ---> [NR, LAIKAS, ŠALTINIO IP, PASKIRTIES IP, PROTOKOLAS]
        int timestampColIndex = 1;
        int sourceColIndex = 2;
        int destinationColIndex = 3;
        int protocolColIndex = 4;

        Timestamp timestamp = getTimestampFromString(cols[timestampColIndex]);
        packet.setTimestamp(timestamp);
        packet.setSource(cols[sourceColIndex]);
        packet.setDestination(cols[destinationColIndex]);
        packet.setProtocol(cols[protocolColIndex]);

        return packet;
    }

    private Timestamp getTimestampFromString(String timestampString) throws GeneralException {
        try {
            String[] parts = timestampString.split("\\.");
            Date parsedDate = simpleDateFormat.parse(parts[0]);
            Timestamp timestamp = new Timestamp(parsedDate.getTime());
            timestamp.setNanos(Integer.parseInt(parts[1]) * 1000);
            return timestamp;
        } catch (Exception e) {
            throw new GeneralException("Blogas datos formatas!", e);
        }
    }

    @Override
    @Transactional
    public String getEntropy(Timestamp start, Timestamp end, Integer increment, Integer windowWidth) throws GeneralException {
        List<PacketsInfo> packetsInfo = packetDao.findPacketCounts(start, end, increment);
        StorageByDestinationInTimeDomain storage = getStorageWithCalculatedEntropy(packetsInfo, windowWidth);

        /* Pasiemame entropijos reikšmes */
        List<ValueInTimeInterval> valuesInTimeIntervals = storage.getListOfEntropies();
        StringBuilder result = new StringBuilder();
        for (ValueInTimeInterval v : valuesInTimeIntervals) {
            result.append(v.getTime()).append("\t").append(v.getValue()).append("\n");
        }
        return result.toString();
    }

    @Override
    public String calculateMutualInformationReturnOptimalTimeDelay(Timestamp start, Timestamp end, Integer increment
            , Integer windowWidth, List<Integer> pointCountList) throws GeneralException {
        //List<PacketsInfo> packetsInfo = packetDao.findPacketCounts(start, end, increment);
        //StorageByDestinationInTimeDomain storage = getStorageWithCalculatedEntropy(packetsInfo, windowWidth);

        List<Double> valueList = getSinusoide();//convertToDoubleList(storage.getListOfEntropies());

        StringBuilder result = new StringBuilder();
        List<Integer> localMinimumList = new ArrayList<Integer>();
        int pointsToCalculate = 1000;
        Picture mutualEntropyGraph = new Picture();
        for (int i = 0; i < pointCountList.size(); i++) {
            XYSeries series = getSeries(pointCountList.get(i), pointsToCalculate, valueList);
            mutualEntropyGraph.addSeries(series);

            Double firstLocalMinimum = getFirstLocalMinimum(series.getItems());
            localMinimumList.add(firstLocalMinimum.intValue());
            result.append(getLine("\t", String.valueOf(pointCountList.get(i)), String.valueOf(firstLocalMinimum))).append("\n");
        }

        mutualEntropyGraph.plotLine("X", "Y", "C:\\Users\\K\\Desktop\\Bakalauras\\latex\\paveiksleliai\\mutual_information.png");

        for (int i = 0; i < pointCountList.size(); i++) {
            Picture phaseSpace = new Picture();
            XYSeries phaseSpaceSeries = new XYSeries(pointCountList.get(i));
            for (int j = 0; j < pointCountList.get(i); j++) {
                Double x = valueList.get(j);
                Double y = valueList.get(j + localMinimumList.get(i));
                phaseSpaceSeries.add(x, y);
            }
            phaseSpace.addSeries(phaseSpaceSeries);
            phaseSpace.plotScatter("X", "Y", "C:\\Users\\K\\Desktop\\Bakalauras\\latex\\paveiksleliai\\phase_space_" + pointCountList.get(i) + ".png");
        }

        return result.toString();
    }

    private Double getFirstLocalMinimum(List<XYDataItem> items) {
        Double lastValue = items.get(0).getYValue();
        for (int i = 0; i < items.size(); i++) {
            Double value = items.get(i).getYValue();
            if (value > lastValue) {
                return items.get(i).getXValue();
            }
            lastValue = value;
        }
        return 0.0;
    }

    private XYSeries getSeries(int dimension, int pointsToCalculate, List<Double> valueList) throws GeneralException {
        List<Double> firstList = valueList.subList(0, dimension);

        XYSeries series = new XYSeries(String.valueOf(dimension));
        for (int i = 0; i < pointsToCalculate; i++) {
            List<Double> secondList = valueList.subList(i, i + dimension);
            Double mutualInformation = new MutualInformationTable(firstList, secondList).getMutualInformation();
            series.add(i, mutualInformation);
        }

        return series;
    }

    private List<Double> convertToDoubleList(List<ValueInTimeInterval> valueInTimeIntervalList) {
        List<Double> doubleList = new ArrayList<Double>();
        for (ValueInTimeInterval valueInTimeInterval : valueInTimeIntervalList) {
            doubleList.add(valueInTimeInterval.getValue());
        }
        return doubleList;
    }

    private StorageByDestinationInTimeDomain getStorageWithCalculatedEntropy(List<PacketsInfo> packetsInfo, int windowWidth) {
        StorageByDestinationInTimeDomain storage = new StorageByDestinationInTimeDomain();

        if (packetsInfo != null && !packetsInfo.isEmpty()) { //Tiesiog patikrinam ar grįžo kas nors iš duombazės
            storage.setWindowWidth(windowWidth); //Nustatome lango plotį, kitaip būtų naudojamas defaultinis

            for (int i = 0; i < packetsInfo.size(); i++) { //Važiuojam per paketų informaciją saugojančius objektus
                PacketsInfo pi = packetsInfo.get(i);

                /* Jeigu apdorojamo objekto laikas pasikeitė, tarkim visi prieš tai apdorojami paketai
                * buvo iš pirmos sekundės, o dabar jau gavome paketą iš antros sekundės, tai dabartinio
                * interalo paketus išsisaugojame atminyje, ir išvalome dabartinio laiko paketų masyvą,
                * kad galėtumėme saugoti naujus paketus */
                if (storage.timeExceedsCurrentTime(pi.getTime())) {
                    storage
                            .addCurrentIntervalToStorage()
                            .cleanCurrentInterval();
                }

                /* Tiesiog paduodame dabartinį objektą apdorojimui */
                storage.addNewPacketInfo(pi);
            }
        }
        return storage;
    }

    private String getLine(String separator, String... columns) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                line.append(separator);
            }
            line.append(columns[i]);
        }
        return line.toString();
    }

    @Override
    public String getPredictionParams(Timestamp start, Timestamp end, Integer increment, Integer windowWidth
            , Integer dimensionCount, List<Integer> pointCount, Integer optimalTimeDelay, Double startAt, Integer pointsToPredict) throws GeneralException {

        List<PacketsInfo> packetsInfo = packetDao.findPacketCounts(start, end, increment);
        StorageByDestinationInTimeDomain storage = getStorageWithCalculatedEntropy(packetsInfo, windowWidth);

        List<Double> valueList = //getSinusoide();
        convertToDoubleList(storage.getListOfEntropies());

        XYSeries realValueSeries = new XYSeries(pointsToPredict);
        XYSeries predictedValueSeries = new XYSeries(pointsToPredict);

        Integer pointCountas = pointCount.get(0);

        Integer size = pointCountas + optimalTimeDelay * dimensionCount;
        Integer startIndex = new Double(size * startAt).intValue();
        double[] valueArray = new double[size];
        for (int index = 0; index < size; index++) {
            valueArray[index] = valueList.get(index);
        }

        for (int i = startIndex; i < size; i++) {
            realValueSeries.add(i, valueArray[i]);
            predictedValueSeries.add(i, valueArray[i]);
        }
        for (int i = 0; i < pointsToPredict; i++) {
            double[][] coeficientMatrix = getCoeficientMatrix(valueArray, dimensionCount, pointCountas, optimalTimeDelay);
            double[][] Y2Matrix = new double[dimensionCount + 1][1];
            Y2Matrix[0][0] = 1.0;
            for (int j = 1; j < dimensionCount + 1; j++) {
                Y2Matrix[j][0] = valueArray[valueArray.length - j * optimalTimeDelay];
            }

            double[][] predictedMatrix = multiplyMatrixes(coeficientMatrix, Y2Matrix);
            double predictedValue = predictedMatrix[0][0];
            double nextRealValue = valueList.get(size + i);

            realValueSeries.add(i + size, nextRealValue);
            predictedValueSeries.add(i + size, predictedValue);

            System.arraycopy(valueArray, 1, valueArray, 0, valueArray.length - 1);
            valueArray[valueArray.length - 1] = predictedValue;
        }

        new Picture()
                .addSeries(realValueSeries)
                .plotLine("X", "Y", pictureDirectory + "generated_real_values.png");
        new Picture()
                .addSeries(predictedValueSeries)
                .plotLine("X", "Y", pictureDirectory + "generated_predicted_values.png");

        return "SUCCESS";
    }

    private List<Double> getSinusoide() {
        List<Double> sinusoide = new ArrayList<Double>();

//        for (int i = 0; i < 200; i++) {
//            for (int j = 0; j < 200; j++) {
//                sinusoide.add((double) j);
//            }
//        }

        double step = 0.03;
        for (int i = 0; i < 1E5; i++) {
            Double value = Math.sin(step * i * Math.PI) + 2;
            sinusoide.add(value);
        }

        return sinusoide;
    }

    private double[][] getCoeficientMatrix(double[] valueArray, Integer dimensionCount, Integer pointCountas, Integer optimalTimeDelay) throws GeneralException {
        Integer startingPosition = optimalTimeDelay * dimensionCount - 1;
        double[][] matrixB = new double[dimensionCount + 1][pointCountas];
        double[][] matrixD = new double[1][pointCountas];

        for (int i = 0; i < dimensionCount + 1; i++) {
            double[] row = new double[pointCountas];
            //List<Double> row = new ArrayList<Double>();
            for (int j = 0; j < pointCountas; j++) {
                Double value;
                if (i == 0) {
                    value = 1.0;
                } else {
//                    int indexToTake = j - (i - 1) * optimalTimeDelay;
//                    if (indexToTake < 0) {
//                        value = 0.0;
//                    } else {
//                        value = valueArray[indexToTake];
//                    }
                    value = valueArray[startingPosition - ((i - 1) * optimalTimeDelay) + j];
                }
                row[j] = value;
            }
            matrixB[i] = row;

            if (i == 1) {
                System.arraycopy(row, 1, matrixD[0], 0, row.length - 1);
                //matrixD[0][row.length - 1] = valueArray[pointCountas - 1];
                matrixD[0][row.length - 1] = valueArray[startingPosition + pointCountas];
            }
        }

        return getCoeficientMatrix(matrixB, matrixD);
//        double[][] invertedMatrixB = invertedMatrixB = inverseMatrix(matrixB);
//        double[][] coefficientMartixA = multiplyMatrixes(matrixD, invertedMatrixB);
//        return coefficientMartixA;
    }

    private double[][] getCoeficientMatrix(double[][] matrixB, double[][] matrixD) {
        QRDecomposition qrDecomposition = new QRDecomposition(
                new BlockRealMatrix(matrixB)
        );

        RealMatrix matrixQT = qrDecomposition.getQT();
        RealMatrix matrixR = qrDecomposition.getR();

        RealMatrix matrixRinverse = new QRDecomposition(matrixR).getSolver().getInverse();

        RealMatrix coeficientMatrix = new BlockRealMatrix(matrixD)
                .multiply(matrixRinverse)
                .multiply(matrixQT);


        return coeficientMatrix.getData();
    }

    private double[][] nestedListsToMatrix(List<List<Double>> nestedLists) {
        double[][] matrix = new double[nestedLists.size()][];

        int i = 0;
        for (List<Double> list : nestedLists) {
            double[] row = new double[list.size()];
            int j = 0;
            for (Double value : list) {
                row[j++] = value.doubleValue();
            }
            matrix[i++] = row;
        }

        return matrix;
    }

    private double[][] inverseMatrix(double[][] matrix) {
        RealMatrix realMatrix = new BlockRealMatrix(matrix);
        RealMatrix inverse = new QRDecomposition(realMatrix).getSolver().getInverse();

        double[][] inverseData = inverse.getData();
        return inverseData;
    }

    private double[][] multiplyMatrixes(double[][] first, double[][] second) {
        RealMatrix firstMatrix = new BlockRealMatrix(first);
        RealMatrix secondMatrix = new BlockRealMatrix(second);

        return firstMatrix.multiply(secondMatrix).getData();
    }

    private String matrixToString(double[][] data) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                result.append("\t").append(data[i][j]);
            }
            result.append("\n");
        }
        return result.toString();
    }
}
