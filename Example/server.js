require('react-native/setupBabel')();
const ip = require('ip');
const glob = require('glob');
const yazl = require('yazl');
const rimraf = require('rimraf');
const mkdirp = require('mkdirp');
const config = require('react-native/local-cli/core');
const bundle = require('react-native/local-cli/bundle/bundle');
const chokidar = require('chokidar');

const HOST = `http://${ip.address()}:3000`;
process.env.HOST = HOST;

rimraf.sync('dist/');
mkdirp.sync('dist/ios');
mkdirp.sync('dist/android');

let isRun = false;
chokidar.watch(['index.android.js', 'index.ios.js', 'src'])
  .on('all', build);

const Koa = require('koa');
const app = new Koa();

function zipFolder(cwd) {
  return new Promise((resolve, reject) => {
    glob('**/*', { cwd, nodir: true }, (err, files) => {
      if (err) return reject(err);
      const zip = new yazl.ZipFile();
      files.forEach(file => zip.addFile(cwd + file, file));
      zip.end();
      resolve(zip.outputStream);
    });
  });
}

app.use(async ctx => {
  if (ctx.path === '/android.ppk') {
    ctx.type = 'zip';
    ctx.body = await zipFolder('dist/android/');
    return;
  }
  if (ctx.path === '/ios.ppk') {
    ctx.type = 'zip';
    ctx.body = await zipFolder('dist/ios/');
    return;
  }
  ctx.body = {
    android: {
      '0.0.1': {
        url: `${HOST}/android.ppk`
      }
    },
    ios: {
      '0.0.1': {
        url: `${HOST}/ios.ppk`
      }
    }
  };
});

app.listen(3000);

function build() {
  if (isRun) return;
  isRun = true;
  const ios = bundle.func(null, config, {
    dev: true,
    platform: 'ios',
    entryFile: 'index.ios.js',
    bundleOutput: 'dist/ios/main.jsbundle',
    assetsDest: 'dist/ios/'
  });
  const android = bundle.func(null, config, {
    dev: true,
    platform: 'android',
    entryFile: 'index.android.js',
    bundleOutput: 'dist/android/index.android.bundle',
    assetsDest: 'dist/android/'
  });

  Promise.all([ios, android]).then(() => {
    isRun = false;
    console.log('Bundle done.');
  }).catch(e => {
    isRun = false;
    console.error(e);
  });
}