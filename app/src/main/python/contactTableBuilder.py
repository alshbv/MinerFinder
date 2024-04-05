import os
import pandas as pd

def find_duplicate_entries(folder_name):
    internal_storage_path = os.path.expanduser("~")

    csv_folder = os.path.join(internal_storage_path, folder_name)

    # Check if the folder exists, and create it if it doesn't
    if not os.path.exists(csv_folder):
        os.makedirs(csv_folder)

    csv_files = [f for f in os.listdir(csv_folder) if f.endswith('.csv')]

    all_entries = []

    for file in csv_files:
        file_path = os.path.join(csv_folder, file)
        df = pd.read_csv(file_path)
        all_entries.append(df)

    merged_df = pd.concat(all_entries, ignore_index=True)

    #apply(lambda x: f"Miner {str(x)}" if isinstance(x, (str, int, float)) else str(x))

    duplicate_entries = merged_df[merged_df.duplicated(subset=['Location', 'Time'], keep=False)]

    if not duplicate_entries.empty:
        duplicate_entries = duplicate_entries.sort_values(by="Location")
        grouped_duplicates = duplicate_entries.groupby(['Time', 'Location']).agg({'ID': lambda x: '-'.join(map(str, x))}).reset_index()

        # Convert the DataFrame to CSV string with the sorted and aggregated data
        # but first, sort it properly
        grouped_duplicates = grouped_duplicates.reindex(columns=['ID', 'Location', 'Time'])
        csv_string = grouped_duplicates.to_csv(index=False)

        return csv_string
    else:
        return "No duplicate entries found."