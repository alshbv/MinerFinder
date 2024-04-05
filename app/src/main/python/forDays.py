import os
import csv
import chaquopy
from com.chaquo.python import Python
import pandas as pd

def main(input_file):
    selected_columns = ["id", "time1", "speed", "angle", "edge"]

    output_file = "filtered_data.csv"

    dir1 = str(Python.getPlatform().getApplication().getFilesDir())

    folder_name = "Raw Data"

    # path to the abovementioned directory, where we store initial data in
    # initial_path = os.path.join(os.path.dirname(__file__), input_name)
    dir1 = str(Python.getPlatform().getApplication().getFilesDir())

    initial_path = os.path.join(dir1, folder_name)
    initial_path_2 = os.path.join(initial_path, input_file)

    # Check if the folder "Raw Data" exists, and if not, create it
    if not os.path.exists(initial_path):
        os.mkdir(initial_path)



    destination_name = "Data"
    data_path = os.path.join(dir1, destination_name)


    # Check if "Data" exists, and if not, create it
    if not os.path.exists(data_path):
        os.mkdir(data_path)

    # Create the full path to the file
    filepath = os.path.join(data_path, input_file)
