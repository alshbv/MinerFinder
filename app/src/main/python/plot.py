import torch
import torch.nn as nn
import pandas as pd
import networkx as nx
import matplotlib.pyplot as figure
import torch.optim as optim
# from utils import preprocess_graph
# from changesToGraph import changesToGraph
import time
# from utils1 import preprocess_graph, mask_test_edges, get_roc_score
import numpy as np
import scipy.sparse as sp
# from model import GCNModelVAE
# from optimizer import loss_function

import pickle as pkl

import networkx as nx

from os.path import dirname, join
import os

## imports for file writing
import json
import chaquopy
from com.chaquo.python import Python

# import torch
# import torch.nn as nn
import torch.nn.functional as F

print("PyTorch version:", torch.__version__)
class changesToGraph(nn.Module):

    def __init__(self, hidden_dim):

        super(changesToGraph, self).__init__()

        self.linear1 = nn.Linear(3, 64)
        self.linear2 = nn.Linear(64, 128)

        self.rnn = nn.LSTM(input_size=128, hidden_size=hidden_dim, output_size=10, dropout=0.3)

    def forward(self, data, hidden, cell):
        outs1 = F.relu(self.linear1(data))
        outs2 = F.relu(self.linear2(outs1))
        print("inide sizes")
        print(hidden.size())
        print(cell.size())
        print("data size is", outs2.size(), "hello")
        print(outs2.size())


        outputs, (hidden, cell) = self.rnn(outs2, (hidden, cell)) # added .unsqueeze

        return torch.sigmoid(hidden), torch.sigmoid(cell)

## end changes to graph

## optimizer

# import torch
import torch.nn.modules.loss
# import torch.nn.functional as F


def loss_function(preds, labels, mu, logvar, n_nodes, norm, pos_weight):
    cost = norm * F.binary_cross_entropy_with_logits(preds, labels, pos_weight=pos_weight)

    # see Appendix B from VAE paper:
    # Kingma and Welling. Auto-Encoding Variational Bayes. ICLR, 2014
    # https://arxiv.org/abs/1312.6114
    # 0.5 * sum(1 + log(sigma^2) - mu^2 - sigma^2)
    KLD = -0.5 / n_nodes * torch.mean(torch.sum(
        1 + 2 * logvar - mu.pow(2) - logvar.exp().pow(2), 1))
    return cost + KLD


## end optimizer

## model

# import torch
# import torch.nn as nn
# import torch.nn.functional as F
from torch.nn.modules.module import Module
from torch.nn.parameter import Parameter


class GraphConvolution(Module):
    """
    Simple GCN layer, similar to https://arxiv.org/abs/1609.02907
    """

    def __init__(self, in_features, out_features, dropout=0.1, act=F.relu):
        super(GraphConvolution, self).__init__()
        self.in_features = in_features
        self.out_features = out_features
        self.dropout = dropout
        self.act = act
        self.weight = Parameter(torch.FloatTensor(in_features, out_features))
        self.reset_parameters()

    def reset_parameters(self):
        torch.nn.init.xavier_uniform_(self.weight)

    def forward(self, input, adj):
        input = F.dropout(input, self.dropout, self.training)
        print("input in mid", input)
        print("is input nan?", torch.isnan(input).any())
        # self.weight = self.weight.grad.clamp(-5, 5)
        support = torch.mm(input, self.weight)
        print("Weights are", self.weight)

        print("input is", input, "support is", support, "adj is", adj)
        # , "output is", output.size())


        output = torch.sparse.mm(adj, support)
        print("output is", output)

        output = F.relu(output)
        return output

    def __repr__(self):
        return self.__class__.__name__ + ' (' \
               + str(self.in_features) + ' -> ' \
               + str(self.out_features) + ')'

class InnerProductDecoder(nn.Module):
    """Decoder for using inner product for prediction."""

    def __init__(self, dropout, act=torch.sigmoid):
        super(InnerProductDecoder, self).__init__()
        self.dropout = dropout
        self.act = act

    def forward(self, z):
        z = F.dropout(z, self.dropout, training=self.training)
        adj = torch.sigmoid(torch.mm(z, z.t()))
        return adj

class GCNModelVAE(nn.Module):
    def __init__(self, input_feat_dim, hidden_dim1, hidden_dim2, dropout):
        super(GCNModelVAE, self).__init__()
        self.gc1 = GraphConvolution(input_feat_dim, hidden_dim1, dropout, act=F.relu)
        self.gc2 = GraphConvolution(hidden_dim1, hidden_dim2, dropout, act=lambda x: x)
        self.gc3 = GraphConvolution(hidden_dim1, hidden_dim2, dropout, act=lambda x: x)
        self.dc = InnerProductDecoder(dropout, act=lambda x: x)

    def encode(self, x, adj):
        hidden1 = self.gc1(x, adj)
        return self.gc2(hidden1, adj), self.gc3(hidden1, adj)

    def reparameterize(self, mu, logvar):
        if self.training:
            std = torch.exp(logvar)
            eps = torch.randn_like(std)
            return eps.mul(std).add_(mu)
        else:
            return mu

    def forward(self, x, adj):
        mu, logvar = self.encode(x, adj)
        z = self.reparameterize(mu, logvar)
        return self.dc(z), mu, logvar

## end model

## utils1

# import networkx as nx
# import numpy as np
import scipy.sparse as sp
# import torch
from sklearn.metrics import roc_auc_score, average_precision_score, accuracy_score
from scipy.special import softmax

def load_data(dataset):
    # load the data: x, tx, allx, graph
    names = ['x', 'tx', 'allx', 'graph']
    objects = []
    for i in range(len(names)):
        '''
        fix Pickle incompatibility of numpy arrays between Python 2 and 3
        https://stackoverflow.com/questions/11305790/pickle-incompatibility-of-numpy-arrays-between-python-2-and-3
        '''
        with open("data/ind.{}.{}".format(dataset, names[i]), 'rb') as rf:
            u = pkl._Unpickler(rf)
            u.encoding = 'latin1'
            cur_data = u.load()
            objects.append(cur_data)
        # objects.append(
        #     pkl.load(open("data/ind.{}.{}".format(dataset, names[i]), 'rb')))
    x, tx, allx, graph = tuple(objects)
    test_idx_reorder = parse_index_file(
        "data/ind.{}.test.index".format(dataset))
    test_idx_range = np.sort(test_idx_reorder)

    if dataset == 'citeseer':
        # Fix citeseer dataset (there are some isolated nodes in the graph)
        # Find isolated nodes, add them as zero-vecs into the right position
        test_idx_range_full = range(
            min(test_idx_reorder), max(test_idx_reorder) + 1)
        tx_extended = sp.lil_matrix((len(test_idx_range_full), x.shape[1]))
        tx_extended[test_idx_range - min(test_idx_range), :] = tx
        tx = tx_extended

    features = sp.vstack((allx, tx)).tolil()
    features[test_idx_reorder, :] = features[test_idx_range, :]
    features = torch.FloatTensor(np.array(features.todense()))
    adj = nx.adjacency_matrix(nx.from_dict_of_lists(graph))

    return adj, features


def parse_index_file(filename):
    index = []
    for line in open(filename):
        index.append(int(line.strip()))
    return index


def sparse_to_tuple(sparse_mx):
    if not sp.isspmatrix_coo(sparse_mx):
        sparse_mx = sparse_mx.tocoo()
    coords = np.vstack((sparse_mx.row, sparse_mx.col)).transpose()
    values = sparse_mx.data
    shape = sparse_mx.shape
    return coords, values, shape


def mask_test_edges(adj):
    # Function to build test set with 10% positive links
    # NOTE: Splits are randomized and results might slightly deviate from reported numbers in the paper.
    # TODO: Clean up.

    # Remove diagonal elements
    adj = adj - sp.dia_matrix((adj.diagonal()[np.newaxis, :], [0]), shape=adj.shape)
    adj.eliminate_zeros()
    # Check that diag is zero:
    assert np.diag(adj.todense()).sum() == 0

    adj_triu = sp.triu(adj)
    adj_tuple = sparse_to_tuple(adj_triu)
    edges = adj_tuple[0]
    edges_all = sparse_to_tuple(adj)[0]
    num_test = int(np.floor(edges.shape[0] / 1.))
    num_val = int(np.floor(edges.shape[0] / 2.))

    # if len(edges) < 20:
    #     num_test =1
    #     num_val = 1
    # if num_val == 0:
    #     num_val = 5
    # if num_test == 0:
    #     num_test = 10

    if num_test == 0:
        num_test = 1



    all_edge_idx = list(range(edges.shape[0]))
    np.random.shuffle(all_edge_idx)

    val_edge_idx = all_edge_idx[:num_val]
    test_edge_idx = all_edge_idx[num_val:(num_val + num_test)]
    test_edges = edges[test_edge_idx]
    val_edges = edges[val_edge_idx]

    if len(edges) == 1:
        test_edges = edges[0]
        val_edges = edges[0]



    # print("train is", len(edges), "num_val is", num_val)
    if len(val_edges) == 0:
        num_val = 1
        val_edge_idx = all_edge_idx[:0]
        test_edge_idx = all_edge_idx[num_val:(0 + num_test)]
        test_edges = edges[test_edge_idx]
        val_edges = edges[val_edge_idx]

    if len(test_edge_idx) == 0:
        pass


    # print("edges", edges, "test", test_edge_idx, "val", val_edge_idx)

    train_edges = edges

    if len(edges) != 1:
        train_edges = np.delete(edges, np.hstack([test_edge_idx, val_edge_idx]), axis=0)


    # print("train is",val_edge_idx)

    def ismember(a, b, tol=5):
        rows_close = np.all(np.round(a - b[:, None], tol) == 0, axis=-1)
        return np.any(rows_close)

    test_edges_false = []
    val = 0
    while len(test_edges_false) < len(test_edges):

        print("i guess i m stuck here")
        # print("val is", val)
        if val >= 1:
            test_edges_false.append([idx_i, idx_j])
            break

        if len(test_edges_false) == 0:
            test_edges_false.append(test_edges[0])

        # print(test_edges_false, " ", test_edges)

        idx_i = np.random.randint(0, adj.shape[0])
        idx_j = np.random.randint(0, adj.shape[0])

        # if len(test_edges_false) == 0:
        #     break
        if val > 1:
            test_edges_false.append([idx_i, idx_j])
            break


        if idx_i == idx_j:
            continue
        if ismember([idx_i, idx_j], edges_all):
            val += 1
            continue
        if test_edges_false:
            if ismember([idx_j, idx_i], np.array(test_edges_false)):
                val += 1
                continue
            if ismember([idx_i, idx_j], np.array(test_edges_false)):
                val += 1
                continue

        test_edges_false.append([idx_i, idx_j])
        val=val+1

    val_edges_false = []

    val = 0
    while len(val_edges_false) < len(val_edges):

        # print("val is", val)
        print("i guess i m stuck here as well")

        if val >= 1:
            val_edges_false.append([idx_i, idx_j])
            break

        if len(val_edges_false) == 0:
            val_edges_false.append(val_edges[0])

        idx_i = np.random.randint(0, adj.shape[0])
        idx_j = np.random.randint(0, adj.shape[0])
        if idx_i == idx_j:
            val = val+1
            continue
        if ismember([idx_i, idx_j], train_edges):
            val = val + 1
            continue
        if ismember([idx_j, idx_i], train_edges):
            val = val + 1
            continue
        if ismember([idx_i, idx_j], val_edges):
            val = val + 1
            continue
        if ismember([idx_j, idx_i], val_edges):
            val = val + 1
            continue
        if val_edges_false:
            if ismember([idx_j, idx_i], np.array(val_edges_false)):
                continue
            if ismember([idx_i, idx_j], np.array(val_edges_false)):
                continue

        val_edges_false.append([idx_i, idx_j])
        val = val + 1


    # assert ~ismember(test_edges_false, edges_all)
    # assert ~ismember(val_edges_false, edges_all)
    #
    # assert ~ismember(val_edges, train_edges)
    # assert ~ismember(test_edges, train_edges)
    # assert ~ismember(val_edges, test_edges)
    if len(val_edges_false) == 0:
        val_edges_false.append(all_edge_idx[:0])

    data = np.ones(train_edges.shape[0])

    # Re-build adj matrix
    adj_train = sp.csr_matrix((data, (train_edges[:, 0], train_edges[:, 1])), shape=adj.shape)
    adj_train = adj_train + adj_train.T

    print("adj train", adj_train)

    # NOTE: these edge lists only contain single direction of edge!
    return adj_train, train_edges, val_edges, val_edges_false, test_edges, test_edges_false


def preprocess_graph(adj):
    adj = sp.coo_matrix(adj)
    adj_ = adj + sp.eye(adj.shape[0])
    rowsum = np.array(adj_.sum(1))
    degree_mat_inv_sqrt = sp.diags(np.power(rowsum, -0.5).flatten())
    adj_normalized = adj_.dot(degree_mat_inv_sqrt).transpose().dot(degree_mat_inv_sqrt).tocoo()
    # return sparse_to_tuple(adj_normalized)
    return sparse_mx_to_torch_sparse_tensor(adj_normalized)


def sparse_mx_to_torch_sparse_tensor(sparse_mx):
    """Convert a scipy sparse matrix to a torch sparse tensor."""
    sparse_mx = sparse_mx.tocoo().astype(np.float32)
    indices = torch.from_numpy(
        np.vstack((sparse_mx.row, sparse_mx.col)).astype(np.int64))
    values = torch.from_numpy(sparse_mx.data)
    shape = torch.Size(sparse_mx.shape)
    return torch.sparse.FloatTensor(indices, values, shape)


def get_roc_score(emb, adj_orig, edges_pos, edges_neg):
    def sigmoid(x):
        return 1 / (1 + np.exp(-x))

    # Predict on test set of edges
    adj_rec = np.dot(emb, emb.T)
    preds = []
    pos = []
    pos_loc = []

    print("adj rec is", adj_rec.shape)

    print("edges pos", edges_pos)

    if len(edges_pos) == 0:
        edges_pos.append([0,0])

    for e in edges_pos:
        # print(type(e))
        if (isinstance(e, np.int32)):
            preds.append(sigmoid(adj_rec[0, 1]))
            # print("e[0]", e)
            pos.append(adj_orig[e, e])
            pos_loc.append([e, e])
        else:
            preds.append(sigmoid(adj_rec[e[0], e[1]]))
            pos.append(adj_orig[e[0], e[1]])
            pos_loc.append([e[0], e[1]])
        # preds.append(adj_rec[e[0], e[1]])

        # print(type(adj_orig))
        # print("what is this?",e[0])


    preds_neg = []
    neg = []

    # if len(edges_neg) == 0:

    for e in edges_neg:
        if (isinstance(e, np.int32)):
            preds.append(sigmoid(adj_rec[0, 1]))
            # print("e[0]", e)
            neg.append(adj_orig[e, e])
            pos_loc.append([e, e])
        else:
            preds_neg.append(sigmoid(adj_rec[e[0], e[1]]))
            pos.append(adj_orig[e[0], e[1]])
            pos_loc.append([e[0], e[1]])
        # preds_neg.append(adj_rec[e[0], e[1]])


    preds_all = np.hstack([preds, preds_neg])
    labels_all = np.hstack([np.ones(len(preds)), np.zeros(len(preds_neg))])

    # preds_all =
    for i, val in enumerate(preds_all):
        if np.isnan(val) == True:
            preds_all[i] = 1


    print("pos is", pos)

    print("label is", labels_all, " ", "preds", preds_all)

    roc_score = roc_auc_score(labels_all, preds_all)
    # roc_score = 0
    # accuracy = accuracy_score(labels_all, preds_all)
    ap_score = average_precision_score(labels_all, preds_all)

    return roc_score, ap_score, preds_all, pos_loc



####### end utils 1

# df = pd.read_csv("final_data")
df1 = pd.DataFrame()
# df2 = pd.DataFrame()
# df7 = pd.read_csv("finally7.csv")
# df0 = pd.read_csv("finally_0.csv")
# df1_1 = pd.read_csv("finally_1.csv")
# df2 = pd.read_csv("finally_2.csv")
# df3 = pd.read_csv("finally_3.csv")
# df4 = pd.read_csv("finally_4.csv")
# df5 = pd.read_csv("finally_5.csv")

# for i in range(25):
#
#     node0 = pd.read_csv(f"node{i}.csv")
#     df1 = pd.concat([df1, node0], ignore_index=True)

# df1 = pd.read_csv("data_25.csv")
username = "short_short_data_25.csv"
filename = join(dirname(__file__), username)
df1 = pd.read_csv(filename)
sum_ap = {}
sum_roc = {}
c = 0
# df2 = pd.read_csv("data_50.csv")

print("columns is", df1.columns)

print("df1 is", df1.head())

# scaler = torch.cuda.amp.GradScaler()

# df.head()pr
# print("df columns", df1.columns)



df1 = df1.drop(columns = ['Unnamed: 0', 'Unnamed: 0.1'])#6s data with others
# df1 = df1.drop(columns = ['Unnamed: 0.1'])
# df1 = df1.drop(columns = ['Unnamed: 0']) #9s data
# df1 = df1.drop(columns = ['Unnamed: 0.1'])
# df7 = df7.drop(columns = ['Unnamed: 0.1', 'Unnamed: 0', 'Unnamed: 8'])#7s data
# df0 = df0.drop(columns = ['Unnamed: 0'])
# df1_1 = df1_1.drop(columns = ['Unnamed: 0', 'Unnamed: 8'])
# df2 = df2.drop(columns = ['Unnamed: 0', 'Unnamed: 8', 'Unnamed: 9'])
# df3 = df3.drop(columns = ['Unnamed: 0'])
# df4 = df4.drop(columns = ['Unnamed: 0'])
# df5 = df5.drop(columns = ['Unnamed: 0'])



# df7 = df7.dropna()
# print(df7)
# print("df7",df7.head())
#
# print("yes?", df3.isnull().values.any())

# data_made = data_made.dropna()

# df, df1, df7, df0, df1_1, df2, df3, df4, df5
# df = df.dropna()
df1 = df1.dropna()
# df1_1 = df1_1.dropna()
# df2 = df2.dropna()
# df3 = df3.dropna()
# df4 = df4.dropna()
# df5= df5.dropna()
# df7 = df7.dropna()
# df0 = df0.dropna()



# print("isna",df4[df4.isna().any(axis = 1)])
# print("isna",df5[df5.isna().any(axis = 1)])

# print("data made is", data_made.head())
print("columns of df1 is", df1.columns)

# exit()
# print( gxc"columns of df2 is", df2.columns)
# print("columns of df3 is", df3.columns)
# print("columns of df4 is", df4.columns)
# print("columns of df5 is", df5.columns)
# print("columns of df is", df.columns)
# print("columns of df1 is", df1.columns)
# print("columns of df0 is", df0.columns)


dt = df1['edge'].value_counts().to_dict()

print("values counts are", len(dt))
# exit()

# df1_1['edge'] = df1_1['edge'].replace(['638006553.0', '380204369.0'], '734407389.0')

# df1_1["edge"].replace({"638006553": "734407389.0", "377803779": "734407389.0", "631706543": "734407389.0",
#                     "300703067": "734407389.0", "655906562":"734407389.0"}, inplace=True)

# dt = df1_1['edge'].value_counts().to_dict()

print("values counts are", dt)
# print("values counts are", df2['edge'].value_counts())
# print("values counts are", df3['edge'].value_counts())
# print("values counts are", df4['edge'].value_counts())
# print("values counts are", df5['edge'].value_counts())


# exit()


data_made = df1
# data_made = pd.concat([df, df1, df7, df0, df1_1, df2, df3, df4, df5, df, df1, df7,df0, df1_1, df2, df3, df4, df5], ignore_index=True)

                       # df, df1, df7,df0, df1_1, df2, df3, df4, df5,
                       # df, df1, df7, df0, df1_1, df2, df3, df4, df5,
                       # df, df1, df7, df0, df1_1, df2, df3, df4, df5,
                       # df, df1, df7, df0, df1_1, df2, df3, df4, df5

data_made.dropna()
print("shape of data made is", data_made.shape)

data_made = data_made.drop(columns = ['latitude', 'longitude'])

# print("cols are",df7.columns)



x = data_made['id']

for i, each in data_made.iterrows():
    if np.isnan(each['id']):
        # print("val is", each)
        # print("type is", type(data_made))
        data_made = data_made.drop(data_made.index[i])

        #potential change
        #data_made = data_made.dropna(subset = ['id'])


# print("none",np.isnan(data_made))

data_made = data_made.dropna()
# df.head()
lst_miners = data_made['id'].unique()
print("miners are", lst_miners)
# lst_miners
data_per_node = {}

print("total data columns, ", data_made.columns)


# np.save("data_50.npy", data_made)
# exit()

# data_small = df.head(300)

# print("data small",data_small)
# print("done")

# print("data types is", df.dtypes)

# df['edge'] = df['edge'].astype(str)
# df['traj_future'] = df['traj_future'].astype(str)

# np.save("final_data_numpy.npy", data_made.to_numpy())

# exit()

# print("data types is", df.dtypes)

G = nx.from_pandas_edgelist(data_made, source = 'edge', target = 'traj_future', create_using=nx.DiGraph())
figure.Figure(figsize=(10, 8))
pos = nx.spring_layout(G)
# nx.draw(G, pos)
node_labels = nx.get_node_attributes(G,'edge')
# nx.draw_networkx_labels(G, pos, font_size = 10)
# plt.show()

# exit()
print(data_made['edge'])
environment = nx.adjacency_matrix(G)

print("num of nodes", len(np.unique(G.nodes)))

# print("Sparse matrix", sp.csr_matrix(environment))

nodes_dict = {}

for i, each in enumerate(G.nodes()):
    nodes_dict[each] = i

lst_nodes = list(nodes_dict.keys())
print("lst nodes", len(lst_nodes))


# print("nodes is", nodes_dict)

# print("nodes are", nodes_dict)

# embeds = nn.Embedding(lst_nodes, 10)

def create_mat(mat_original, graph, mapping):

    froms = []
    tos = []
    # data = []
    mats = np.ndarray(mat_original.shape)

    for e in graph.edges().data():
        # print("Edge is", e[0], " ", e[1])

        from_loc = mapping.index(e[0])
        to_loc = mapping.index(e[1])

        mats[from_loc][to_loc] = 1


    return mats


T = nx.Graph()
dc = pd.DataFrame()

dict_data = {}



data_copy = data_made.copy()


LSTM_data_per_node = {}

for each in lst_nodes:
    LSTM_data_per_node[each] = (torch.zeros([10, 128]), torch.zeros([10, 128]))


for each in lst_miners:

    print("Currently for", each)

    model = GCNModelVAE(256, 16, 32, 0.000001)

    data = data_made[data_made['id'] == each]
    # dict_data['id'] = df[df['id'] == each]

    dc = data_made[data_made['id'] == each]

    # adj_original = nx.adjacency_matrix()
    nodes_dict = {}

    print("time is", dc['time1'])

    # exit()

    unique_time = [np.unique(dc['time1'])][0]
    # print("time is", unique_time)

    timed_data = {}

    n_nodes = len(lst_nodes)
    embeds = nn.Embedding(n_nodes, 10)

    feat_mat_made = np.zeros((n_nodes, 256))

    dict_scores = {}
    c=0

    for i, each_t in enumerate(unique_time):

        timed_data[each] = dc[dc['time1'] == each_t]
        data_curr = timed_data[each].copy()
        G_dc = nx.Graph()

        G_dc = nx.from_pandas_edgelist(timed_data[each], source = 'edge', target = 'traj_future', create_using=nx.Graph())
        # print("data curr", data_curr)
        feat_mat = data_curr[['angle', 'speed', 'time']]

        #accumalate information per node

        locations = np.unique(data_curr['edge'])
        print("Test locations - ", data_curr['edge'])
        print(locations)

        # for i, each_row in data_curr:


 #|-########################training LSTM for data agg########################
        # c = 0
        # LSTM_model = changesToGraph(128)
        ctg_filepath = "ChangesToGraphMobile.ptl"
        print("Before loading model")
        ctg_filepath = join(dirname(__file__), "ChangesToGraphMobile1-8-1--2.ptl")
        if os.path.exists(ctg_filepath):
            print("THE FILE EXISTS")
        else:
            print("THE FILE DOES NOT EXIST")
        LSTM_model = torch.jit.load(ctg_filepath)
        # loss_f = nn.NLLLoss()
        # optimizer = optim.Adam(model.parameters(), lr=0.00001)

        for each_loc in locations:

            print("Working for time", each_t, "and place = ", each_loc)

            data_per_node = data_curr[data_curr['edge'] == each_loc]

            print("Error check #1: Data_per_node", data_per_node)
            print("Error check #2: Data_per_node", data_per_node.mean())
            print("Error check #3: Data_per_node", data_per_node.std())

            ##if (np.isnan(data_per_node.std().any())):
            ## if (data_per_node.std().any() == np.nan):
            std_values = data_per_node.std()
            if np.any(np.isnan(data_per_node.std())):
                print("converting to ones...")
                std_values = np.nan_to_num(std_values, nan=0.0)
                print(std_values)
                continue

            #normalized_data_per_node=(data_per_node-data_per_node.mean())/data_per_node.std()
            print("Error check #4: Data_per_node", data_per_node)
            print("Error check #5: Mean Data_per_node", data_per_node.mean())
            print("Error check #6: STD Data_per_node", data_per_node.std())
            normalized_data_per_node=(data_per_node-data_per_node.mean())/std_values
            normalized_data_per_node = normalized_data_per_node.dropna()

            # node_embedding.append(embeds(torch.tensor([fo], dtype=torch.long)))

            data_send = data_per_node[['speed', 'angle', 'time']]

            # data_send['speed']=(data_send['speed']-data_send['speed'].min())/(data_send['speed'].max()-data_send['speed'].min())
            # data_send['angle']=(data_send['angle']-data_send['angle'].min())/(data_send['angle'].max()-data_send['angle'].min())
            # data_send['time']=(data_send['time']-data_send['time'].min())/(data_send['time'].max()-data_send['time'].min())

            # next_node = data_per_node['traj_future']
            # data_send = data_send.reshape((2, ))
            # data_send = data_send.T
            print("data send shape is", data_send)
            # print("Data is", data_send)
            data_send_to = torch.from_numpy(np.array(data_send, dtype = np.float64))

            # LSTM_model.train()

            lst_LSTM = []
            if each_loc in LSTM_data_per_node:

                tup = LSTM_data_per_node[each_loc]
                lst_LSTM.append(tup[0])
                lst_LSTM.append(tup[1])

                # lst_LSTM.append(torch.zeros(1, 128))
            print("lstm is", lst_LSTM)
            print("INPUT: ", data_send_to.float(), lst_LSTM[0], lst_LSTM[1])
            # with open("lstm_input_samp.txt", "w") as f:
            #     f.write(str(data_send_to.float()))
            #     f.write("BREAK" + str(data_send_to.size()))
            #     f.write(str(lst_LSTM[0]))
            #     f.write("BREAK" + str(len(lst_LSTM[0])) + "," + str(len(lst_LSTM[0][1])))
            #     f.write(str(lst_LSTM[1]))
            #     f.write(str(lst_LSTM[1].size()))

            data_send_to = data_send_to.unsqueeze(1)
            lst_LSTM[0] = lst_LSTM[0].unsqueeze(1)
            lst_LSTM[1] = lst_LSTM[1].unsqueeze(1)

            hid, c = LSTM_model(data_send_to.float(), lst_LSTM[0], lst_LSTM[1])

            print("OUTPUT: ", hid, c)
            # with open("lstm_output_samp.txt", "w") as f:
            #     f.write(str(hid))
            #     f.write("BREAK")
            #     f.write(str(c))
            # exit()
            # torch.nn.utils.clip_grad_norm_(LSTM_model.parameters(), 1)

            # print("hidden is", hid.cpu().detach().numpy())
            hid_get = hid.cpu().detach().numpy()

            print("hidden lstm is",hid_get)
            # print("nan in hidden?", np.isnan(hid_get))

            # print("cell is", c.cpu().detach().numpy())
            cell_get = c.cpu().detach().numpy()
            print("cell lstm is",cell_get)
            # print("nan in hidden?", np.isnan(cell_get))

            # print("type of df is", type(feat_mat_made))

            # comb = np.vstack((hid_get))


            for x in range(128):
                # print('SHape')
                # print(hid_get.s)
                hid_get = hid_get.squeeze()
                # hid_get[0][x] = hid_get[0][x].squeeze()
                # hid_get[0][x] = hid_get[0][x].reshape((1,128))
                feat_mat_made[lst_nodes.index(each_loc)][x] = hid_get[0][x]

            for x in range(128):
                cell_get = cell_get.squeeze()

                feat_mat_made[lst_nodes.index(each_loc)][x+128] = cell_get[0][x]

            #accumalate information per node###################
            # print("unique nodes in dataset", np.unique(data_curr['edge']))

        print("Shape of feat_mat is", feat_mat_made)

        print("Came here11")

########################training LSTM for data agg########################-|

        G_dc_next = nx.Graph()
        keys_list = list(unique_time)
        # print("keys is", keys_list)
        if i+1 >= len(unique_time):
            break

        key = keys_list[i+1]

        print("next is", unique_time[i+1])

        dt = dc[dc['time1'] == key]

        G_dc_next = nx.from_pandas_edgelist(dt, source = 'edge', target = 'traj_future', create_using=nx.Graph())

        print("Came here1111")

        now_mat = create_mat(environment, G_dc, lst_nodes)
        next_mat = create_mat(environment, G_dc_next, lst_nodes)

        feat_mat_made_used = feat_mat_made


        adj, features = sp.csr_matrix(environment), torch.tensor(feat_mat_made_used)
        print(adj)

        # print("features are", features.shape)

        n_nodes, feat_dim = features.shape

        # Store original adjacency matrix (without diagonal entries) for later
        adj_orig = sp.csr_matrix(environment)
        adj_orig = adj_orig - sp.dia_matrix((adj_orig.diagonal()[np.newaxis, :], [0]), shape=adj_orig.shape)
        adj_orig.eliminate_zeros()

        adj_train, train_edges, val_edges, val_edges_false, test_edges, test_edges_false = mask_test_edges(adj)

        adj = adj
        print("adj is", adj_train, "shape is", adj.shape)

        # Some preprocessing
        print("Came here before preprocess start")
        adj_norm = preprocess_graph(adj)
        print("adj norm")
        print(adj_norm)
        adj_label = adj_train + sp.eye(adj_train.shape[0])
        # adj_label = sparse_to_tuple(adj_label)
        adj_label = torch.FloatTensor(adj_label.toarray())



        pos_weight = float(adj.shape[0] * adj.shape[0] - adj.sum()) / adj.sum()
        norm = adj.shape[0] * adj.shape[0] / float((adj.shape[0] * adj.shape[0] - adj.sum()) * 2)

        print("Came here before model start")
        print("Dimensions of features are", feat_dim)

        # model.zero_grad()


        optimizer = optim.Adam(model.parameters(), lr=0.00001)

        hidden_emb = None

        feat_mat_made_used = torch.from_numpy(feat_mat_made_used).float()
        print("features is", feat_mat_made)

        # for epoch in range(1):
        t = time.time()
        model.train()
        optimizer.zero_grad()

        print("adj norm is", adj_norm)

        recovered, mu, logvar = model(feat_mat_made_used, adj_norm)

        adj_label = dc[dc['time1'] == unique_time[i+1]]

        # loss = nn.CrossEntropyLoss(now_mat, next_mat)

        print("recovered is", recovered, " ", recovered.size())

        print("adj label", adj_label, " ", adj_label.shape, " ", type(adj_label))

        print("Came here after model before loss")

        loss = loss_function(preds=recovered, labels=torch.tensor(next_mat),
                             mu=mu, logvar=logvar, n_nodes=n_nodes,
                             norm=norm, pos_weight=torch.tensor(pos_weight, dtype=torch.long))
        loss.backward()
        cur_loss = loss.item()
        # clipped_lr = lr * clip_gradient(model, clip)

        # model.parameters = torch.nn.utils.clip_grad_norm(model.parameters(), 5)
        # torch.nn.utils.clip_grad_norm_(model.parameters(), 5)

        # scaler.step(optimizer)
        optimizer.step()

        hidden_emb = mu.data.numpy()
        roc_curr, ap_curr, _, _ = get_roc_score(hidden_emb, adj_orig, val_edges, val_edges_false)

        print("Epoch:", '%04d' % (0 + 1), "train_loss=", "{:.5f}".format(cur_loss),
              "val_ap=", "{:.5f}".format(ap_curr),
              "time=", "{:.5f}".format(time.time() - t)
              )

        print("Optimization Finished!")

        print("hidden is", hidden_emb)
        roc_score, ap_score, predicted1, pos_loc = get_roc_score(hidden_emb, adj_orig, test_edges, test_edges_false)

        if each_t not in sum_roc.keys():
            sum_roc[each_t] = 0
            sum_ap[each_t] = 0

        sum_roc[each_t] = sum_roc[each_t]+roc_score
        sum_ap[each_t] = sum_ap[each_t]+ap_score
        c = c+1

        # if not dict_scores[unique_time[i]]:
        #     dict_scores[unique_time[i]] = roc_score
        # else:
        #     dict_scores[unique_time[i]] += roc_score
        #
        # c+=1
        print('Test ROC score: ' + str(roc_score))
        print('Test AP score: ' + str(ap_score))

        # print("Avg till now is", dict_scores[unique_time[i]]/c)
        # print("Accuracy is", str(accuracy))

        ## My additions ##
        results = {}
        if each_t not in results:
            results[each_t] = [(predicted1, pos_loc)]
        else:
            lst = results[each_t]
            lst.append((predicted1, pos_loc))
            results[each_t] = lst
        ## My additions ##


    for each_val in sum_ap.keys():

        print(sum_ap[each_val]/c)
        print(sum_roc[each_val]/c)

    from statistics import mean

    t_ap = 0
    for s, v in sum_ap.items():
        t_ap+=v


    t_roc = 0
    for s, v in sum_roc.items():
        # print(mean(v))
        t_roc+=v

# for k, v in t_ap.values():
#     print(v / c)
#
# for k, v in t_roc.values():
#     print(v / c)



####################
### MY ADDITIONS ###
####################

conv_list = {}
for i in range(len(locations)):
    conv_list[i] = locations[i]

print("OUTOUT", conv_list)
print("OUTOUT", results)


res_updated = {}
for k, v in results.items():
    if k == '':
        print("came here")
        k = '9-10'
        # k.replace('', '9-10')
    res_updated[k] = v

print("RES_UPDATED CONTENTS: ", res_updated)

final_res = {}

print("nodes are: ", conv_list)

for k, v in res_updated.items():

    probs = v[0][0]
    edges = v[0][1]

    print("probabilities: ", probs)

    ind = np.where(np.max(probs))[0][0]
    #ind = np.argmax(probs)
    print("valid indexes are: ", np.where(np.max(probs))[0])
    print("index is", ind)
    print("edges is", edges)
    print("conv list", conv_list)
    print(" - ")
    try:
        print("Entered try block")

        # this was an experiment to check if I'd avoid the errors
        # I ran into due to mismatched location indices. I don't think it is correct.
        #edge_dict = data_curr['edge'].to_dict()
        #traj_future_dict = data_curr['traj_future'].to_dict()
        #print("edgedict", edge_dict)
        #print("trajfuturedict", traj_future_dict)

        from_loc = conv_list[edges[ind][0]]
        #from_loc = edge_dict[edges[ind][0]]

        print("1: print from_loc ", from_loc)
        to_loc = conv_list[edges[ind][1]]
        #to_loc = traj_future_dict[edges[ind][1]]

        print("2: print to_loc", to_loc)
        # e = edges[ind]

        final_res[k] = (probs[ind], (from_loc, to_loc))
        print("3: print array at ind", final_res[k])
    except Exception as e:
        print("Exception: ", e)


print("finally contact", final_res)
final_df = pd.DataFrame(final_res.items(), columns=['time', 'probs'])
print(final_df)

# Create a new dataframe
new_df = pd.DataFrame()

# Add the 'time' column from the old dataframe to the new dataframe
new_df['time'] = final_df['time']

# Split the 'probs' column and create two new columns
new_df['prob'] = [item[0] for item in final_df['probs']]
new_df['location'] = [item[1] for item in final_df['probs']]

username_0 = lst_miners[0]
username_0final = "miner" + str(username_0)
username_1 = username.split(".")[0]
new_df.insert(0, "userID", username_0final)
print(new_df)

## outputs and saves the file
filename = username_1 + "results.csv"
dir1 = str(Python.getPlatform().getApplication().getFilesDir())
filepath = os.path.join(dir1, filename)

new_df.to_csv(filepath, index=False)

print("final: ", res_updated)