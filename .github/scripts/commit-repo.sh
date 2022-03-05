#!/bin/bash
set -e

rsync -a --delete --exclude .git --exclude .gitignore ./repo/ .
git config --global user.email "github-actions[bot]@users.noreply.github.com"
git config --global user.name "github-actions[bot]"
git status

cd repo
git add .
git commit -m "Updated Extensions"
git push
