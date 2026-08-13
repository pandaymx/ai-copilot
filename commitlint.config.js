module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [
      2,
      'always',
      [
        'feat',
        'fix',
        'refactor',
        'style',
        'docs',
        'chore',
        'ci',
        'test',
        'perf',
        'build',
        'revert',
      ],
    ],
    'scope-enum': [
      2,
      'always',
      ['backend', 'frontend', 'ci', 'docs', 'deps', 'release', 'root'],
    ],
    'subject-empty': [2, 'never'],
    'type-empty': [2, 'never'],
    'header-max-length': [2, 'always', 100],
  },
};
