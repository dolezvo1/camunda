import React from 'react';

import {LabeledInput, ErrorMessage} from 'components';

import './NumberInputs.css';

export default class NumberInputs extends React.Component {
  state = {
    tooLow: false
  };

  static sanitizeData = ({target, baseline}) => {
    return {
      target: parseFloat(target),
      baseline: parseFloat(baseline)
    };
  };

  componentDidMount() {
    if (this.props.configuration.targetValue.values) {
      return this.props.setData({...this.props.configuration.targetValue.values});
    }
    return this.props.setData({
      target: 100,
      baseline: 0
    });
  }

  isValid = value => {
    if (typeof value === 'number') {
      return true;
    }
    if (typeof value === 'string') {
      return value.trim() && !isNaN(value.trim()) && +value >= 0;
    }
  };

  isTooLow = (baseline, target) => {
    if (!this.isValid(baseline) || !this.isValid(target)) {
      return false;
    }
    return parseFloat(target) <= parseFloat(baseline);
  };

  change = type => ({target: {value}}) => {
    const newData = {...this.props.data, [type]: value};

    const isValid = this.isValid(value);
    const tooLow = this.isTooLow(newData.baseline, newData.target);

    this.props.setData(newData);
    this.props.setValid(isValid);
    this.setState({tooLow});
  };

  render() {
    const tooLow = this.state.tooLow;
    const baselineInvalid = !this.isValid(this.props.data.baseline);
    const targetInvalid = !this.isValid(this.props.data.target);
    return (
      <React.Fragment>
        <LabeledInput
          className="NumberInputs__input"
          label="Baseline"
          value={this.props.data.baseline || 0}
          onChange={this.change('baseline')}
          isInvalid={baselineInvalid}
        >
          {baselineInvalid && <ErrorMessage>Must be a non-negative number</ErrorMessage>}
        </LabeledInput>
        <LabeledInput
          className="NumberInputs__input"
          label="Target"
          value={this.props.data.target || 0}
          onChange={this.change('target')}
          isInvalid={targetInvalid || tooLow}
        >
          {targetInvalid && <ErrorMessage>Must be a non-negative number</ErrorMessage>}
          {tooLow && <ErrorMessage>Target must be greater than baseline</ErrorMessage>}
        </LabeledInput>
      </React.Fragment>
    );
  }
}
