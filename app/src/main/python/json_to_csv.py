import json
import csv
import datetime
import pandas as pd
import os
from os.path import dirname, join
import random
import chaquopy
from com.chaquo.python import Python





def convert_json_to_csv(json_file_path, csv_file_path, username):
    with open(json_file_path, 'r') as json_file:
        json_data = json.load(json_file)
        print(json_data)  

    with open(csv_file_path, 'w', newline='') as csv_file:
        csv_writer = csv.writer(csv_file)

        user_name = username
        # Write the header row
        header = ['','Unnamed: 0', 'id', 'latitude', 'longitude', 'time',	'edge', 'speed', 'traj_future',	'angle', 'time1']
        csv_writer.writerow(header)

        count = 0
        for timestamp, value in json_data.items():
            split_values = value.split(',')  # Split the value into three parts using the csv
            
            # Extract the whole seconds part from the timestamp string
            seconds_only = timestamp.split('.')[0]

            # Convert the timestamp to seconds after 12 midnight on that day
            dt = datetime.datetime(*map(int, seconds_only.split(' ')[0].split('-')), *map(int, seconds_only.split(' ')[1].split(':')))
            #print(dt)
            #midnight = dt.replace(hour=0, minute=0, second=0, microsecond=0)
            #seconds_after_midnight = int((dt - midnight).total_seconds())
            midnight = dt.replace(hour=0, minute=0, second=0, microsecond=0)
            seconds_after_midnight = int((dt - midnight).total_seconds())


            ## updates time entries
            ## seconds_after_midnight = count*60

            ## 1 denotes a column deliberately left empty! These are placeholders either dropped from the model or to be filled later
            row = [count] + [count] + [user_name] + [1] + [1] + [seconds_after_midnight] + split_values[2:] + split_values[1:2] + split_values[2:] + split_values[0:1] + [1] # Create the row with converted timestamp and split values
            csv_writer.writerow(row)

            ## updates count
            count = count + 1

        #row = [count + 1] + [count + 1] + [user_name] + [1] + [1] + [seconds_after_midnight + 60] + ["B"] + [1.2] + ["P"] + [184.3] + [1] # Create the row with converted timestamp and split values
        csv_writer.writerow(row)

        


def last_row_edits(csv_file_path):
    ## now the file is filled, modify the future trajectories
    # Read the CSV file
    df = pd.read_csv(csv_file_path)

    # Shift edge values by one position and assign them to the future trajectory 
    # the last value will be NAN
    df['traj_future'] = df['edge'].shift(-1)

    # Write the modified DataFrame back to a CSV file
    df.to_csv(csv_file_path, index=False)


def time_range_create(csv_file_path):
    # Read the CSV file into a DataFrame and read all time values
    df = pd.read_csv(csv_file_path)
    time_values = df['time']

    # Find the highest and lowest values in the column
    highest_time = time_values.max()
    lowest_time = time_values.min()

    range_val = highest_time - lowest_time

    # as 9-5 has 8 blocs, we take octants of our range.
    octant_range = 3600

    #df.loc[(df['time'] >= 0), 'time1'] = "9-10"
    df.loc[(df['time'] >= 0) & (df['time'] <= octant_range), 'time1'] = "9-10"
    df.loc[(df['time'] >= octant_range) & (df['time'] <= octant_range*2), 'time1'] = "10-11"
    df.loc[(df['time'] >= octant_range*2) & (df['time'] <= octant_range*3), 'time1'] = "11-12"
    df.loc[(df['time'] >= octant_range*3) & (df['time'] <= octant_range*4), 'time1'] = "12-1"
    df.loc[(df['time'] >= octant_range*4) & (df['time'] <= octant_range*5), 'time1'] = "1-2"
    df.loc[(df['time'] >= octant_range*5) & (df['time'] <= octant_range*6), 'time1'] = "2-3"
    df.loc[(df['time'] >= octant_range*6) & (df['time'] <= octant_range*7), 'time1'] = "3-4"
    df.loc[(df['time'] >= octant_range*7), 'time1'] = "4-5"
    # last line has an exception for the highest value, as rounding may affect it

    # Write the modified DataFrame back to a CSV file
    df.to_csv(csv_file_path, index=False)

def swap_letters_in_csv(file_path, edge, future_edge):
    # Generate random mappings for letters
    letter_mapping = {chr(letter): random.randint(350000000, 450000000) for letter in range(ord('a'), ord('z')+1)}

    # Read the CSV file
    with open(file_path, 'r') as csvfile:
        reader = csv.DictReader(csvfile)
        rows = list(reader)

    # Swap letters in the specified column
    for row in rows:
        if edge in row:
            original_value = row[edge]
            swapped_value = ''.join([str(letter_mapping.get(letter, letter)) for letter in original_value])
            row[edge] = swapped_value
        if future_edge in row:
            original_value = row[future_edge]
            swapped_value = ''.join([str(letter_mapping.get(letter, letter)) for letter in original_value])
            row[future_edge] = swapped_value


    # Write the modified data back to the CSV file
    with open(file_path, 'w', newline='') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=reader.fieldnames)
        writer.writeheader()
        writer.writerows(rows)

## j gets read as an imaginary
def letter_fix(csv_file_path):
    df = pd.read_csv(csv_file_path)
    column_name1 = 'edge'
    column_name2 = "traj_future"

    df[column_name1] = df[column_name1].replace('J', 'Z')
    df[column_name2] = df[column_name2].replace('J', 'Z')
    # Save the modified DataFrame back to the CSV file
    df.to_csv(csv_file_path, index=False)


# not needed anymore, was used for testing in old versions
def add_dummy_entries(csv_file_path):
    # Read the CSV file into a DataFrame
    df = pd.read_csv(csv_file_path)

    # Get the number of times to copy the columns
    num_copies = 7
    holder_df = df.copy()

    # Copy the existing columns and update the [time] column
    for i in range(0,num_copies):
        new_df = df.copy()  # Create a copy of the DataFrame

        # Update the [time] column by adding 3600 to each value
        new_df['time'] += 3600 * (i + 1)

        # Append the new DataFrame to the original DataFrame
        holder_df = pd.concat([holder_df, new_df], ignore_index=True)

    # Write the updated DataFrame back to the CSV file
    df = holder_df
    df.to_csv(csv_file_path, index=False)


# run code
def main(filename):
    dir1 = str(Python.getPlatform().getApplication().getFilesDir())

    folder_name = "Raw Data"

    # path to the "Data" directory and the directory we store initial data in
    initial_path = os.path.join(dir1, filename)
    data_path = os.path.join(dir1, folder_name)


    # Check if "Data" exists, and if not, create it
    if not os.path.exists(data_path):
        os.mkdir(data_path)

    # Create the full path to the file, and the username
    parts = filename.split('.')
    name = parts[0] + ".csv"
    username = parts[0]

    csv_filepath = os.path.join(data_path, name)



    #json_file_path = 'C:/Users/Upmanyurht/Desktop/pythonstore2/7.json'
    #csv_file_path = 'C:/Users/Upmanyurht/Desktop/pythonstore2/miner7.csv'
    convert_json_to_csv(initial_path, csv_filepath, username)
    ## add_dummy_entries(csv_filepath)
    last_row_edits(csv_filepath)
    time_range_create(csv_filepath)
    letter_fix(csv_filepath)
