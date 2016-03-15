package service;

import exceptions.GeneralException;

import java.sql.Timestamp;

public interface DataService {

    void uploadFileToDatabase(String[] fileNames) throws GeneralException;
    String getEntropy(Timestamp start, Timestamp end, Integer increment, Integer windowWidth) throws GeneralException;

}


