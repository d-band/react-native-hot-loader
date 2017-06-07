import React, { Component } from 'react';
import {
  AppRegistry,
  StyleSheet,
  Text,
  View,
  Alert,
  Image,
  TouchableOpacity,
  Platform
} from 'react-native';
import {
  isFirstTime,
  isRolledBack,
  packageVersion,
  currentVersion,
  checkUpdate,
  downloadUpdate,
  switchVersion,
  switchVersionLater,
  markSuccess
} from 'react-native-hot-loader';

export default class Example extends Component {
  componentWillMount() {
    if (isRolledBack) {
      Alert.alert('提示', '刚刚更新失败了,版本被回滚.');
    } else if (isFirstTime) {
      Alert.alert('提示', '这是当前版本第一次启动,是否要模拟启动失败?将回滚到上一版本', [
        {text: '是', onPress: ()=>{throw new Error('模拟启动失败,请重启应用')}},
        {text: '否', onPress: ()=>{markSuccess()}},
      ]);
    };
  }
  doUpdate = () => {
    fetch(process.env.HOST)
      .then((res) => res.json())
      .then(data => {
        const versions = data[Platform.OS];
        downloadUpdate({
          update: true,
          updateUrl: versions['0.0.1'].url,
          hash: String(Date.now())
        }).then(hash => {
          Alert.alert('提示', '下载完毕,是否重启应用?', [
            {text: '是', onPress: () => {
              switchVersion(hash);
            }},
            {text: '否'},
            {text: '下次启动时', onPress: () => {
              switchVersionLater(hash);
            }}
          ]);
        }).catch(err => {
          console.log(err);
          Alert.alert('提示', '更新失败.');
        });
      });
  };
  render() {
    return (
      <View style={styles.container}>
        <Image source={require('./logo.png')}/>
        <Text style={styles.welcome}>
          Welcome to React Native!
        </Text>
        <Text style={styles.instructions}>
          To get started, edit index.android.js
        </Text>
        <Text style={styles.instructions}>
          Double tap R on your keyboard to reload,{'\n'}
          Shake or press menu button for dev menu
        </Text>
        <TouchableOpacity onPress={this.doUpdate}>
          <Text style={styles.instructions}>
            点击这里更新
          </Text>
        </TouchableOpacity>
      </View>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF',
  },
  welcome: {
    fontSize: 20,
    textAlign: 'center',
    margin: 10,
  },
  instructions: {
    textAlign: 'center',
    color: '#333333',
    marginBottom: 5,
  },
});

AppRegistry.registerComponent('Example', () => Example);
