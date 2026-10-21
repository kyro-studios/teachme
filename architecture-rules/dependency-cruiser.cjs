// フロントエンドのモジュール間の依存のルール (docs/architecture.md の 4.2)
// リポジトリのルートで実行する:
//   architecture-rules/node_modules/.bin/depcruise --config architecture-rules/dependency-cruiser.cjs app/frontend/src

const SRC = '^app/frontend/src';
const SHARED_PARTS = 'ui|board|avatar|audio|image-export';

/** @type {import('dependency-cruiser').IForbiddenRuleType[]} */
const forbidden = [
  {
    name: 'no-circular',
    comment: '循環する依存を作らない',
    severity: 'error',
    from: {},
    to: { circular: true },
  },
  {
    name: 'no-test-import-from-src',
    comment: 'src のファイルから、テストのファイルを import しない',
    severity: 'error',
    from: { path: SRC, pathNot: '\\.test\\.tsx?$' },
    to: { path: '\\.test\\.tsx?$' },
  },
  {
    name: 'app-not-to-features',
    comment: 'app は features に直接依存しない',
    severity: 'error',
    from: { path: `${SRC}/app/` },
    to: { path: `${SRC}/features/` },
  },
  {
    name: 'pages-not-to-app',
    comment: 'pages は app に依存しない',
    severity: 'error',
    from: { path: `${SRC}/pages/` },
    to: { path: `${SRC}/app/` },
  },
  {
    name: 'pages-not-to-pages',
    comment: 'pages どうしで依存しない',
    severity: 'error',
    from: { path: `${SRC}/pages/`, pathNot: '\\.test\\.tsx?$' },
    to: { path: `${SRC}/pages/` },
  },
  {
    name: 'features-not-to-other-features',
    comment: 'features は、ほかの features に依存しない (共通の部分は shared に移す)',
    severity: 'error',
    from: { path: `${SRC}/features/([^/]+)/` },
    to: { path: `${SRC}/features/`, pathNot: `${SRC}/features/$1/` },
  },
  {
    name: 'features-not-to-pages-or-app',
    comment: 'features は pages と app に依存しない',
    severity: 'error',
    from: { path: `${SRC}/features/` },
    to: { path: `${SRC}/(pages|app)/` },
  },
  {
    name: 'shared-parts-not-to-other-shared',
    comment: 'shared の各部品は、自分のディレクトリと shared/lib だけに依存する',
    severity: 'error',
    from: { path: `${SRC}/shared/(${SHARED_PARTS})/` },
    to: {
      path: `${SRC}/shared/`,
      pathNot: [`${SRC}/shared/$1/`, `${SRC}/shared/lib/`],
    },
  },
  {
    name: 'shared-not-to-upper-layers',
    comment: 'shared は features、pages、app、api に依存しない',
    severity: 'error',
    from: { path: `${SRC}/shared/` },
    to: { path: `${SRC}/(features|pages|app|api)/` },
  },
  {
    name: 'shared-lib-only-self',
    comment: 'shared/lib は、自分のディレクトリの中だけに依存する',
    severity: 'error',
    from: { path: `${SRC}/shared/lib/` },
    to: { path: `${SRC}/`, pathNot: `${SRC}/shared/lib/` },
  },
  {
    name: 'api-only-self',
    comment: 'api は shared、features、pages、app に依存しない',
    severity: 'error',
    from: { path: `${SRC}/api/` },
    to: { path: `${SRC}/(shared|features|pages|app)/` },
  },
];

module.exports = {
  forbidden,
  options: {
    doNotFollow: { path: 'node_modules' },
    tsPreCompilationDeps: true,
    enhancedResolveOptions: {
      exportsFields: ['exports'],
      conditionNames: ['import', 'require', 'node', 'default', 'types'],
      mainFields: ['module', 'main', 'types', 'typings'],
    },
  },
};
