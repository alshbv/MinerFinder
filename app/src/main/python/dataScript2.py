import os
import csv
import chaquopy
from com.chaquo.python import Python
import pandas as pd

def filter_csv(input_file, output_file, selected_columns):
    input_file_path = input_file
    output_file_path = output_file

    with open(input_file_path, 'r') as infile, open(output_file_path, 'w', newline='') as outfile:
        reader = csv.DictReader(infile)
        fieldnames = reader.fieldnames
        filtered_fieldnames = [col for col in fieldnames if col in selected_columns]

        writer = csv.DictWriter(outfile, fieldnames=filtered_fieldnames)
        writer.writeheader()

        for row in reader:
            filtered_row = {col: row[col] for col in filtered_fieldnames}
            writer.writerow(filtered_row)



def process_csv_2(input_file, output_file):
    df = pd.read_csv(input_file)

    df = df.dropna()

    # Select the last 4 entries from the DataFrame
    last_4_entries = df.tail(5)[:-1]

    time_column = last_4_entries['time']

    # Perform calculations on the last_4_entries DataFrame
    last_4_entries['time1'] = ((((last_4_entries['time'] / 3600)%8) + 8).astype(int).astype(str)).str.zfill(2) + ':' + (((last_4_entries['time'] % 3600) // 60).astype(int).astype(str)).str.zfill(2)

    last_4_entries.drop(columns=['time'], inplace=True)

    last_4_entries.rename(columns={"time1": "time[hh:mm]"}, inplace=True)
    last_4_entries.rename(columns={"speed": "speed[m/s]"}, inplace=True)
    last_4_entries.rename(columns={"angle": "angle"}, inplace=True)
    last_4_entries.rename(columns={"edge": "location"}, inplace=True)
    last_4_entries.rename(columns={"id": "ID"}, inplace=True)

    # Save the modified data to the output CSV
    last_4_entries.to_csv(output_file, index=False)





    return 1

def process_csv(input_file, output_file):
    df = pd.read_csv(input_file)

    ## Group the data based on unique entries in the "time1" column
    grouped = df.groupby("time1", sort=False)

    ## Process each group and save to a new DataFrame
    modified_data = []
    for _, group in grouped:
        if len(group) >= 4:
            interval = len(group) // 4
            selected_rows = group.iloc[::interval][:4]

            time1_values = []
            for i, time1_value in enumerate(selected_rows["time1"]):
                if "-" in time1_value and time1_value[0].isdigit():
                    fragments = time1_value.split("-")
                    a = int(fragments[0])
                    b = int(fragments[1])
                    smaller_value = min(a, b)
                    if(a == 1 or b == 1):
                        smaller_value = max(a, b)

                    minutes = (i + 1)*15
                    if(minutes >= 60):
                        smaller_value = smaller_value + int((minutes/60))
                        minutes = minutes - 60
                    if(minutes == 0):
                        minutes = "00"
                    if(minutes == 60):
                        minutes = "00"

                    new_value = f"{smaller_value}:{str(minutes)}"
                    time1_values.append(new_value)
            selected_rows["time1"] = time1_values
            modified_data.append(selected_rows)
        else:
            print("else, leave the data identical")
            for index, row in group.iterrows():
                # Append the row to the list
                modified_data.append(row)

    
    # Combine the modified data from all groups into a single DataFrame
    modified_df = pd.concat(modified_data)
    modified_df.rename(columns={"time1": "time[hh:mm]"}, inplace=True)
    modified_df.rename(columns={"speed": "speed[m/s]"}, inplace=True)
    modified_df.rename(columns={"angle": "angle"}, inplace=True)
    modified_df.rename(columns={"edge": "location"}, inplace=True)
    modified_df.rename(columns={"id": "ID"}, inplace=True)



    # Save the modified data to a new CSV file
    modified_df.to_csv(output_file, index=False)


def result_correction(input_file):
    df = pd.read_csv(input_file)
    last_id = df['ID'].iloc[-1]
    last_time = df["time[hh:mm]"].iloc[-1]

    folder_name = "Results"
    filename = "miner" + str(last_id) + "results.csv"
    # path to the abovementioned directory, where we store initial data in
    # initial_path = os.path.join(os.path.dirname(__file__), input_name)
    dir1 = str(Python.getPlatform().getApplication().getFilesDir())

    initial_path = os.path.join(dir1, folder_name)
    initial_path_2 = os.path.join(initial_path, filename)

    if not os.path.exists(initial_path_2):
        print("Doesn't exist! 113456")
        return 1
    
    df2 = pd.read_csv(initial_path_2)

    last_time_2 = df2["time"].iloc[-1]

    result_time = last_time_2.split("-")
    result_hour = min(int(result_time[0]), int(result_time[1]))

    initial_time = last_time.split(":")
    initial_hour = int(initial_time[0])
    print("value comparisons", initial_hour, result_hour)

    given_numbers = [52, 54, 56, 58]

    a = ((result_hour > 8) and (result_hour != initial_hour)) or ( (result_hour <= 8) and (result_hour != (initial_hour - 12)))

    if((a) and (result_hour != 9)):
        target_hour = result_hour - 1
        df['time[hh:mm]'] = df.apply(lambda row: f"{int(target_hour):02d}:{given_numbers[row.name % len(given_numbers)]:02d}", axis=1)
    
    
    df.to_csv(input_file, index=False)
    return 1



def main(input_name):
    selected_columns = ["id","time", "time1", "speed", "angle", "edge"]

    output_file = "filtered_data.csv"

    dir1 = str(Python.getPlatform().getApplication().getFilesDir())

    folder_name = "Raw Data"

    # path to the abovementioned directory, where we store initial data in
    # initial_path = os.path.join(os.path.dirname(__file__), input_name)
    dir1 = str(Python.getPlatform().getApplication().getFilesDir())

    initial_path = os.path.join(dir1, folder_name)
    initial_path_2 = os.path.join(initial_path, input_name)

    # Check if the folder "Raw Data" exists, and if not, create it
    if not os.path.exists(initial_path):
        os.mkdir(initial_path)



    destination_name = "Data"
    data_path = os.path.join(dir1, destination_name)


    # Check if "Data" exists, and if not, create it
    if not os.path.exists(data_path):
        os.mkdir(data_path)

    # Create the full path to the file
    filepath = os.path.join(data_path, input_name)

    filter_csv(initial_path_2, filepath, selected_columns)
    #print("Filtered data saved to 'Data/filtered_data.csv'")

    process_csv_2(filepath, filepath)

    result_correction(filepath)